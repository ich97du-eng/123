package com.example.stunslam;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Stun Slam fuer Minecraft 26.2 (Fabric, nur Client).
 *
 * Solange die Mod AN ist und du faellst, loest sie aus, sobald dein Fadenkreuz auf einem Spieler liegt.
 * Die ganze Kette (Axt schlagen -> Mace waehlen -> Mace schlagen) passiert in EINEM Tick,
 * damit sie auch bei hohem Fall-Tempo klappt (da bleibt das Ziel nur kurz im Fadenkreuz).
 * Danach wechselt sie auf deinen urspruenglichen Slot zurueck.
 *
 * Einstellungen: Menue-Taste (Standard Rechts-Shift).
 */
public class StunSlamClient implements ClientModInitializer {

    public static KeyMapping toggleKey;
    public static KeyMapping menuKey;

    private enum Phase { IDLE, RESTORE }

    private Phase phase = Phase.IDLE;
    private int originalSlot = -1;
    private int modSlot = -1;   // Slot, den die Mod zuletzt selbst gewaehlt hat
    private int timer = 0;
    private int cooldown = 0;

    private double peakY = Double.NaN;
    private double fallDistance = 0;

    @Override
    public void onInitializeClient() {
        StunSlamConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("stunslam", "main"));

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.stunslam.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                category));

        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.stunslam.menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) {
            resetState();
            return;
        }

        while (menuKey.consumeClick()) {
            mc.gui.setScreen(new StunSlamScreen(null));
        }
        while (toggleKey.consumeClick()) {
            StunSlamConfig.enabled = !StunSlamConfig.enabled;
            p.sendSystemMessage(Component.literal(
                    "Stun Slam: " + (StunSlamConfig.enabled ? "AN" : "AUS")));
        }

        trackFall(p);

        if (!StunSlamConfig.enabled) {
            if (phase != Phase.IDLE) {
                restore(p);
            }
            return;
        }
        if (mc.gui.screen() != null) { // in Menues/Inventar nichts ausloesen
            if (phase != Phase.IDLE) {
                restore(p);
            }
            return;
        }

        if (phase == Phase.RESTORE) {
            if (--timer <= 0) {
                restore(p);
            }
            return;
        }

        // ---- Phase IDLE: auf Gelegenheit warten ----
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (StunSlamConfig.requireAttackKey && !mc.options.keyAttack.isDown()) {
            return;
        }
        if (!canSlam(p)) {
            return;
        }
        Player target = lookedAtPlayer(mc, p);
        if (target == null) {
            return;
        }
        runChain(mc, p, target);
    }

    // ---------------- Kette (alles in einem Tick) ----------------

    private void runChain(Minecraft mc, LocalPlayer p, Player target) {
        StunSlamConfig.Mode mode = StunSlamConfig.mode;
        boolean blocking = target.isBlocking();

        if (mode == StunSlamConfig.Mode.SHIELD_ONLY && !blocking) {
            return;
        }
        boolean useAxe = switch (mode) {
            case SHIELD_ONLY, SHIELD_AUTO -> blocking;
            case ALWAYS_AXE -> true;
            case MACE_ONLY -> false;
        };

        int mace = findHotbar(p, true);
        if (mace < 0) {
            return;
        }
        int axe = useAxe ? findHotbar(p, false) : -1;
        if (useAxe && axe < 0) {
            return;
        }

        originalSlot = p.getInventory().getSelectedSlot();

        if (useAxe) {
            select(p, axe);
            attack(mc, p, target);   // Schild brechen
        }
        select(p, mace);
        attack(mc, p, target);       // Smash

        // Der Server setzt die Fallhoehe nach dem Smash zurueck - wir auch.
        peakY = p.getY();
        fallDistance = 0;

        phase = Phase.RESTORE;
        timer = StunSlamConfig.restoreDelayTicks;
        if (timer <= 0) {
            restore(p);
        }
    }

    private void restore(LocalPlayer p) {
        // Nur zurueckwechseln, wenn du den Slot in der Zwischenzeit nicht selbst geaendert hast.
        if (originalSlot >= 0 && p.getInventory().getSelectedSlot() == modSlot) {
            p.getInventory().setSelectedSlot(originalSlot);
        }
        if (phase != Phase.IDLE) {
            cooldown = StunSlamConfig.cooldownTicks;
        }
        phase = Phase.IDLE;
        originalSlot = -1;
        modSlot = -1;
    }

    private void resetState() {
        phase = Phase.IDLE;
        originalSlot = -1;
        modSlot = -1;
        cooldown = 0;
        peakY = Double.NaN;
        fallDistance = 0;
    }

    // ---------------- Hilfsfunktionen ----------------

    private void trackFall(LocalPlayer p) {
        double y = p.getY();
        if (Double.isNaN(peakY) || p.onGround() || p.isInWater() || p.onClimbable()) {
            peakY = y;
        } else {
            peakY = Math.max(peakY, y);
        }
        fallDistance = peakY - y;
    }

    private boolean canSlam(LocalPlayer p) {
        return !p.onGround() && !p.isFallFlying() && fallDistance > StunSlamConfig.minFallDistance;
    }

    private static Player lookedAtPlayer(Minecraft mc, LocalPlayer p) {
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof Player target
                && target != p
                && target.isAlive()
                && !target.isSpectator()) {
            return target;
        }
        return null;
    }

    /** Sucht Mace (mace = true) oder Axt (mace = false) in der Hotbar; bevorzugt den aktuellen Slot. */
    private static int findHotbar(LocalPlayer p, boolean mace) {
        Inventory inv = p.getInventory();
        int selected = inv.getSelectedSlot();
        if (matches(inv.getItem(selected), mace)) {
            return selected;
        }
        for (int i = 0; i < 9; i++) {
            if (matches(inv.getItem(i), mace)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matches(ItemStack stack, boolean mace) {
        return mace ? stack.is(Items.MACE) : stack.is(ItemTags.AXES);
    }

    private void select(LocalPlayer p, int slot) {
        p.getInventory().setSelectedSlot(slot);
        modSlot = slot;
    }

    private static void attack(Minecraft mc, LocalPlayer p, Entity target) {
        mc.gameMode.attack(p, target);
        p.swing(InteractionHand.MAIN_HAND);
    }
}
