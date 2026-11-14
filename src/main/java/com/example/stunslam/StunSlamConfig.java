package com.example.stunslam;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Einstellungen der Mod. Werden in config/stunslam.properties gespeichert. */
public final class StunSlamConfig {

    public enum Mode {
        SHIELD_ONLY("Nur bei Schild: Axt, Mace"),
        SHIELD_AUTO("Auto (ohne Schild nur Mace)"),
        ALWAYS_AXE("Immer Axt, dann Mace"),
        MACE_ONLY("Immer nur Mace");

        public final String label;

        Mode(String label) {
            this.label = label;
        }

        public Mode next() {
            Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    /** Wird absichtlich nicht gespeichert: nach dem Start ist die Mod immer AUS. */
    public static boolean enabled = false;

    public static Mode mode = Mode.SHIELD_AUTO;
    public static double minFallDistance = 1.5;
    public static int restoreDelayTicks = 2;
    public static int cooldownTicks = 4;
    public static boolean requireAttackKey = false;

    private static final double[] FALL_STEPS = {1.5, 2.0, 3.0, 5.0, 8.0};
    private static final int[] RESTORE_STEPS = {0, 1, 2, 3, 5};
    private static final int[] COOLDOWN_STEPS = {0, 2, 4, 8, 20};

    private StunSlamConfig() {
    }

    public static void cycleFall() {
        int i = 0;
        for (int k = 0; k < FALL_STEPS.length; k++) {
            if (FALL_STEPS[k] == minFallDistance) {
                i = k + 1;
            }
        }
        minFallDistance = FALL_STEPS[i % FALL_STEPS.length];
    }

    public static void cycleRestore() {
        int i = 0;
        for (int k = 0; k < RESTORE_STEPS.length; k++) {
            if (RESTORE_STEPS[k] == restoreDelayTicks) {
                i = k + 1;
            }
        }
        restoreDelayTicks = RESTORE_STEPS[i % RESTORE_STEPS.length];
    }

    public static void cycleCooldown() {
        int i = 0;
        for (int k = 0; k < COOLDOWN_STEPS.length; k++) {
            if (COOLDOWN_STEPS[k] == cooldownTicks) {
                i = k + 1;
            }
        }
        cooldownTicks = COOLDOWN_STEPS[i % COOLDOWN_STEPS.length];
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("stunslam.properties");
    }

    public static void load() {
        Path file = file();
        if (!Files.exists(file)) {
            return;
        }
        try (var in = Files.newInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            mode = Mode.valueOf(p.getProperty("mode", mode.name()));
            minFallDistance = Double.parseDouble(p.getProperty("minFallDistance", String.valueOf(minFallDistance)));
            restoreDelayTicks = Integer.parseInt(p.getProperty("restoreDelayTicks", String.valueOf(restoreDelayTicks)));
            cooldownTicks = Integer.parseInt(p.getProperty("cooldownTicks", String.valueOf(cooldownTicks)));
            requireAttackKey = Boolean.parseBoolean(p.getProperty("requireAttackKey", String.valueOf(requireAttackKey)));
        } catch (IOException | RuntimeException e) {
            // kaputte Datei -> Standardwerte behalten
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("mode", mode.name());
        p.setProperty("minFallDistance", String.valueOf(minFallDistance));
        p.setProperty("restoreDelayTicks", String.valueOf(restoreDelayTicks));
        p.setProperty("cooldownTicks", String.valueOf(cooldownTicks));
        p.setProperty("requireAttackKey", String.valueOf(requireAttackKey));
        try (var out = Files.newOutputStream(file())) {
            p.store(out, "Stun Slam");
        } catch (IOException e) {
            // nicht schlimm, dann gilt der Wert nur bis zum Neustart
        }
    }
}
