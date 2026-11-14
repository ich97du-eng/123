package com.example.stunslam;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Einstellungsmenü: Mod an/aus, Modus, Werte und beide Tasten. */
public class StunSlamScreen extends Screen {

    private final Screen parent;

    private Button toggleBtn;
    private Button modeBtn;
    private Button fallBtn;
    private Button restoreBtn;
    private Button cooldownBtn;
    private Button attackKeyBtn;
    private Button toggleKeyBtn;
    private Button menuKeyBtn;

    /** Welche Taste gerade neu belegt wird (null = keine). */
    private KeyMapping waiting;

    public StunSlamScreen(Screen parent) {
        super(Component.literal("Stun Slam"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 280;
        int h = 20;
        int gap = 24;
        int x = this.width / 2 - w / 2;
        int y = Math.max(8, this.height / 2 - 120);

        Button header = Button.builder(Component.literal("Stun Slam - Einstellungen"), b -> {
        }).bounds(x, y, w, h).build();
        header.active = false;
        this.addRenderableWidget(header);
        y += gap;

        toggleBtn = add(x, y, w, h, b -> {
            StunSlamConfig.enabled = !StunSlamConfig.enabled;
            refresh();
        });
        y += gap;

        modeBtn = add(x, y, w, h, b -> {
            StunSlamConfig.mode = StunSlamConfig.mode.next();
            refresh();
        });
        y += gap;

        fallBtn = add(x, y, w, h, b -> {
            StunSlamConfig.cycleFall();
            refresh();
        });
        y += gap;

        restoreBtn = add(x, y, w, h, b -> {
            StunSlamConfig.cycleRestore();
            refresh();
        });
        y += gap;

        cooldownBtn = add(x, y, w, h, b -> {
            StunSlamConfig.cycleCooldown();
            refresh();
        });
        y += gap;

        attackKeyBtn = add(x, y, w, h, b -> {
            StunSlamConfig.requireAttackKey = !StunSlamConfig.requireAttackKey;
            refresh();
        });
        y += gap;

        toggleKeyBtn = add(x, y, w, h, b -> {
            waiting = StunSlamClient.toggleKey;
            refresh();
        });
        y += gap;

        menuKeyBtn = add(x, y, w, h, b -> {
            waiting = StunSlamClient.menuKey;
            refresh();
        });
        y += gap;

        this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> this.onClose())
                .bounds(x, y, w, h).build());

        refresh();
    }

    private Button add(int x, int y, int w, int h, Button.OnPress onPress) {
        return this.addRenderableWidget(
                Button.builder(Component.empty(), onPress).bounds(x, y, w, h).build());
    }

    private void refresh() {
        toggleBtn.setMessage(Component.literal(
                "Stun Slam: " + (StunSlamConfig.enabled ? "AN" : "AUS")));
        modeBtn.setMessage(Component.literal("Modus: " + StunSlamConfig.mode.label));
        fallBtn.setMessage(Component.literal(
                "Mindest-Fallhoehe: " + StunSlamConfig.minFallDistance + " Bloecke"));
        restoreBtn.setMessage(Component.literal(
                "Zurueck auf alten Slot nach: " + StunSlamConfig.restoreDelayTicks + " Ticks"));
        cooldownBtn.setMessage(Component.literal(
                "Pause nach Kette: " + StunSlamConfig.cooldownTicks + " Ticks"));
        attackKeyBtn.setMessage(Component.literal(
                "Nur bei gehaltenem Linksklick: " + (StunSlamConfig.requireAttackKey ? "JA" : "NEIN")));
        toggleKeyBtn.setMessage(keyLabel("Taste An/Aus", StunSlamClient.toggleKey));
        menuKeyBtn.setMessage(keyLabel("Taste Menue", StunSlamClient.menuKey));
    }

    private Component keyLabel(String name, KeyMapping mapping) {
        if (waiting == mapping) {
            return Component.literal(name + ": > Taste druecken (ESC = abbrechen) <");
        }
        return Component.literal(name + ": ").append(mapping.getTranslatedKeyMessage());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (waiting != null) {
            InputConstants.Key key = InputConstants.getKey(event);
            if (key.getValue() != GLFW.GLFW_KEY_ESCAPE) {
                waiting.setKey(key);
                KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
            }
            waiting = null;
            refresh();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        StunSlamConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
