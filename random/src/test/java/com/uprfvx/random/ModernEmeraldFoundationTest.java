package com.uprfvx.random;

import com.uprfvx.romio.romhandlers.ModernEmeraldRomHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

class ModernEmeraldFoundationTest {
    @TempDir Path temp;

    @Test void presetsKeepOnlyVerifiedSettings() {
        Settings settings = new Settings();
        settings.setRomName("Pokemon Modern Emerald 3.6");
        settings.setRandomizeMovePPs(true);
        settings.setRandomizeMoveAccuracies(true);
        settings.setRandomizeMovePowers(true);
        settings.setRandomizeIntroMon(true);
        settings.setRandomizeWildPokemon(true);
        settings.setTrainersMod(Settings.TrainersMod.RANDOM);
        settings.setCurrentMiscTweaks(-1);
        Settings safe = settings.forModernEmerald();
        assertTrue(safe.isRandomizeMovePPs());
        assertTrue(safe.isRandomizeMoveAccuracies());
        assertFalse(safe.isRandomizeMovePowers());
        assertFalse(safe.isRandomizeIntroMon());
        assertTrue(safe.isRandomizeWildPokemon());
        assertEquals(Settings.WildPokemonZoneMod.NONE, safe.getWildPokemonZoneMod());
        assertEquals(Settings.WildPokemonTypeMod.NONE, safe.getWildPokemonTypeMod());
        assertEquals(Settings.WildPokemonEvolutionMod.NONE, safe.getWildPokemonEvolutionMod());
        assertFalse(safe.isBlockWildLegendaries());
        assertEquals(Settings.TrainersMod.UNCHANGED, safe.getTrainersMod());
        assertEquals(0, safe.getCurrentMiscTweaks());
        assertEquals(settings.getRomName(), safe.getRomName());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MODERN_EMERALD_ROM", matches = ".+")
    void entirePipelineWritesOnlySelectedVerifiedBytesAndReloads() throws Exception {
        String source = System.getenv("MODERN_EMERALD_ROM");
        byte[] before = Files.readAllBytes(Path.of(source));
        for (int mode = 0; mode < 4; mode++) {
            var handler = new ModernEmeraldRomHandler();
            assertTrue(handler.loadRom(source));
            Settings settings = new Settings();
            settings.setRomName(handler.getROMName());
            settings.setRandomizeMoveAccuracies((mode & 1) != 0);
            settings.setRandomizeMovePPs((mode & 2) != 0);
            // Imported and programmatic settings cannot invoke any vanilla writer.
            settings.setTrainersMod(Settings.TrainersMod.RANDOM);
            settings.setRandomizeIntroMon(true);
            settings.setRandomizeMovePowers(true);
            settings.setCurrentMiscTweaks(-1);
            settings.setCurrentRestrictions(null); // Also occurs in imported legacy presets.
            assertDoesNotThrow(() -> settings.tweakForRom(handler));
            Path output = temp.resolve("mode-" + mode + ".gba");
            var randomizer = new GameRandomizer(settings, null, handler,
                    ResourceBundle.getBundle("com.uprfvx.random.gui.Bundle"), false);
            var result = randomizer.randomize(output.toString(), new java.io.PrintStream(
                    java.io.OutputStream.nullOutputStream()), 42);
            assertTrue(result.wasSaveSuccessful(), () -> result.getException().toString());
            assertTrue(result.wasLogSuccessful(), () -> result.getLogException().toString());
            byte[] after = Files.readAllBytes(output);
            assertEquals(before.length, after.length);
            int changed = 0;
            for (int offset = 0; offset < before.length; offset++) {
                if (before[offset] == after[offset]) continue;
                changed++;
                int relative = offset - ModernEmeraldRomHandler.MOVE_DATA;
                int id = relative / 12;
                int field = relative % 12;
                assertTrue(id >= 1 && id <= 374 && id != 165, "offset " + offset);
                assertTrue((field == 3 && (mode & 1) != 0) || (field == 4 && (mode & 2) != 0),
                        "unexpected changed offset " + offset);
            }
            assertEquals(mode == 0, changed == 0);
            assertTrue(new ModernEmeraldRomHandler().loadRom(output.toString()));
        }
    }
}
