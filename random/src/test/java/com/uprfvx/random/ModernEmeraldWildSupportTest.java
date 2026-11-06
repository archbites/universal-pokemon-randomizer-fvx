package com.uprfvx.random;

import com.uprfvx.random.randomizers.WildEncounterRandomizer;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.*;
import com.uprfvx.romio.romio.RomOpener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MODERN_EMERALD_ROM", matches = ".+")
class ModernEmeraldWildSupportTest {
    @TempDir Path temp;

    private ModernEmeraldRomHandler load() {
        var handler = new ModernEmeraldRomHandler();
        assertTrue(handler.loadRom(System.getenv("MODERN_EMERALD_ROM")));
        return handler;
    }

    private Settings requestedSettings() {
        Settings settings = new Settings();
        settings.setRomName("Pokemon Modern Emerald 3.6");
        settings.setRandomizeWildPokemon(true);
        settings.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.NONE);
        settings.setWildPokemonTypeMod(Settings.WildPokemonTypeMod.NONE);
        settings.setWildPokemonEvolutionMod(Settings.WildPokemonEvolutionMod.NONE);
        settings.setCatchEmAllEncounters(true);
        return settings.forModernEmerald();
    }

    @Test void everyModeCoversEveryGroupAcrossFortySeedsWithoutChangingLevelsOrRates() {
        var handler = load();
        List<EncounterArea> original = handler.getEncounters(false);
        Set<Integer> formsSeen = new HashSet<>();
        for (int seed = 0; seed < 40; seed++) {
            new WildEncounterRandomizer(handler, requestedSettings(), new Random(seed)).randomizeEncounters();
            List<EncounterArea> after = handler.getEncounters(false);
            List<Set<Integer>> groupsPerMode = List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
            List<Set<Integer>> physicalPerMode = List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
            for (int a = 0; a < after.size(); a++) {
                int mask = ModernEmeraldEncounterData.TABLES.get(a).routeCoverageMask();
                assertEquals(original.get(a).getRate(), after.get(a).getRate());
                assertEquals(original.get(a).size(), after.get(a).size());
                for (int s = 0; s < after.get(a).size(); s++) {
                    Encounter encounter = after.get(a).get(s);
                    int id = handler.getInternalId(encounter.getSpecies());
                    assertTrue(ModernEmeraldSpeciesCatalog.isEligible(id));
                    assertEquals(original.get(a).get(s).getLevel(), encounter.getLevel());
                    assertEquals(original.get(a).get(s).getMaxLevel(), encounter.getMaxLevel());
                    formsSeen.add(id);
                    for (int mode = 0; mode < 3; mode++) {
                        if ((mask & (1 << mode)) != 0) {
                            groupsPerMode.get(mode).add(ModernEmeraldSpeciesCatalog.selectionKey(id));
                            physicalPerMode.get(mode).add(id);
                        }
                    }
                }
            }
            for (int mode = 0; mode < 3; mode++) {
                assertEquals(431, groupsPerMode.get(mode).size(), "seed " + seed + ", mode " + mode);
                assertTrue(physicalPerMode.get(mode).containsAll(List.of(201, 453, 454, 459, 460, 461)));
            }
        }
        assertEquals(434, formsSeen.size()); // Includes all four Deoxys forms across these deterministic seeds.
    }

    @Test void savedRomChangesOnlyWildSpeciesAndAllThreeNativeViewsHaveFullCoverage() throws Exception {
        var handler = load();
        byte[] before = Files.readAllBytes(Path.of(System.getenv("MODERN_EMERALD_ROM")));
        Path output = temp.resolve("wild.gba");
        Settings settings = requestedSettings();
        // A preset cannot enable unverified options alongside the supported wild options.
        settings.setRandomizeMovePowers(true);
        settings.setWildLevelsModified(true);
        settings.setWildLevelModifier(50);
        settings.setTrainersMod(Settings.TrainersMod.RANDOM);
        settings.setUseMinimumCatchRate(true);
        var results = new GameRandomizer(settings, null, handler,
                ResourceBundle.getBundle("com.uprfvx.random.gui.Bundle"), false)
                .randomize(output.toString(), new PrintStream(OutputStream.nullOutputStream()), 123456789L);
        assertTrue(results.wasSaveSuccessful(), () -> results.getException().toString());
        assertTrue(results.wasLogSuccessful(), () -> results.getLogException().toString());
        byte[] after = Files.readAllBytes(output);
        BitSet allowedBytes = new BitSet(before.length);
        for (var table : ModernEmeraldEncounterData.TABLES) {
            for (int slot = 0; slot < table.count(); slot++) {
                int offset = table.speciesOffset(slot);
                allowedBytes.set(offset, offset + 2);
            }
        }
        allowedBytes.set(ModernEmeraldRomHandler.OUTBREAK_MOVES_PATCH,
                ModernEmeraldRomHandler.OUTBREAK_MOVES_PATCH + 2);
        int changes = 0;
        for (int offset = 0; offset < before.length; offset++) {
            if (before[offset] != after[offset]) {
                changes++;
                assertTrue(allowedBytes.get(offset), "Unrelated changed byte " + Integer.toHexString(offset));
            }
        }
        assertTrue(changes > 0);
        assertEquals(0x0B, after[ModernEmeraldRomHandler.OUTBREAK_MOVES_PATCH] & 255);
        assertEquals(0xE0, after[ModernEmeraldRomHandler.OUTBREAK_MOVES_PATCH + 1] & 255);
        assertNativeRouteCoverage(after);
        var reopened = new RomOpener(3).openRomFile(output.toFile());
        assertTrue(reopened.wasOpeningSuccessful());
        var loaded = assertInstanceOf(ModernEmeraldRomHandler.class, reopened.getRomHandler());
        assertTrue(loaded.isRomValid(null));
        Path unchanged = temp.resolve("reloaded-unchanged.gba");
        assertTrue(loaded.saveRom(unchanged.toString(), 0, false));
        assertArrayEquals(after, Files.readAllBytes(unchanged));

        // Check eligibility as well as the layout fingerprint when reading a randomized image.
        byte[] invalid = after.clone();
        int slot = ModernEmeraldEncounterData.TABLES.getFirst().speciesOffset(0);
        invalid[slot] = (byte) 458;
        invalid[slot + 1] = 1;
        assertFalse(ModernEmeraldRomHandler.isSupportedImage(invalid));

        var repeat = load();
        Path repeated = temp.resolve("same-seed.gba");
        var repeatedResults = new GameRandomizer(requestedSettings(), null, repeat,
                ResourceBundle.getBundle("com.uprfvx.random.gui.Bundle"), false)
                .randomize(repeated.toString(), new PrintStream(OutputStream.nullOutputStream()), 123456789L);
        assertTrue(repeatedResults.wasSaveSuccessful());
        assertArrayEquals(after, Files.readAllBytes(repeated));
    }

    /** Independent of the adapter manifest: follow the binary headers and the native 3.6 mode selector. */
    private void assertNativeRouteCoverage(byte[] image) {
        for (int mode = 0; mode < 3; mode++) {
            Set<Integer> groups = new HashSet<>();
            Set<Integer> physical = new HashSet<>();
            for (int routeMap = 16; routeMap <= 49; routeMap++) {
                int base = -1;
                for (int header = 0xBDD84C; (image[header] & 255) != 255; header += 20) {
                    if (image[header] == 0 && (image[header + 1] & 255) == routeMap) { base = header; break; }
                }
                assertTrue(base >= 0);
                int selected = base + mode * 20;
                if (mode == 2 && (image[selected] != image[base] || image[selected + 1] != image[base + 1])) {
                    selected = base + 20; // Night falls back to modern day for routes without a night table.
                }
                assertEquals(routeMap, image[selected + 1] & 255);
                int[] counts = {12, 5, 5, 10};
                for (int type = 0; type < 4; type++) {
                    int pointer = word32(image, selected + 4 + type * 4);
                    if (pointer == 0) continue;
                    int info = pointer - 0x08000000;
                    if (image[info] == 0) continue;
                    int data = word32(image, info + 4) - 0x08000000;
                    for (int slot = 0; slot < counts[type]; slot++) {
                        int offset = data + slot * 4 + 2;
                        int id = (image[offset] & 255) | ((image[offset + 1] & 255) << 8);
                        physical.add(id);
                        groups.add(ModernEmeraldSpeciesCatalog.selectionKey(id));
                    }
                }
            }
            assertEquals(431, groups.size(), "Native view " + mode);
            assertTrue(physical.containsAll(List.of(201, 453, 454, 459, 460, 461)));
        }
    }

    private static int word32(byte[] image, int offset) {
        return (image[offset] & 255) | ((image[offset + 1] & 255) << 8)
                | ((image[offset + 2] & 255) << 16) | ((image[offset + 3] & 255) << 24);
    }

    @Test void maximumReplacementsDoNotMapEveryOccurrenceOfASpeciesToOneReplacement() {
        var handler = load();
        List<EncounterArea> before = handler.getEncounters(false);
        Settings settings = requestedSettings();
        settings.setCatchEmAllEncounters(false);
        new WildEncounterRandomizer(handler, settings, new Random(7)).randomizeEncounters();
        List<EncounterArea> after = handler.getEncounters(false);
        Map<Integer, Set<Integer>> replacements = new HashMap<>();
        for (int area = 0; area < before.size(); area++) {
            for (int slot = 0; slot < before.get(area).size(); slot++) {
                int original = handler.getInternalId(before.get(area).get(slot).getSpecies());
                replacements.computeIfAbsent(original, _ -> new HashSet<>())
                        .add(handler.getInternalId(after.get(area).get(slot).getSpecies()));
            }
        }
        assertTrue(replacements.values().stream().anyMatch(set -> set.size() > 100));
    }
}
