package com.uprfvx.random;

import com.uprfvx.random.randomizers.*;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MODERN_EMERALD_ROM", matches = ".+")
class ModernEmeraldEvolutionSupportTest {
    @TempDir Path temp;
    private ModernEmeraldRomHandler load() {
        var handler = new ModernEmeraldRomHandler();
        assertTrue(handler.loadRom(System.getenv("MODERN_EMERALD_ROM")));
        return handler;
    }
    private Settings settings(boolean catchAll) {
        var settings = new Settings();
        settings.setRomName("Pokemon Modern Emerald 3.6");
        settings.setRandomizeWildPokemon(true);
        settings.setCatchEmAllEncounters(catchAll);
        settings.setBanPrematureEvos(true);
        return settings.forModernEmerald();
    }

    @Test void minimumLevelsFollowEveryActualMethodAndMissingLinksStayBasic() {
        var handler = load();
        var policy = handler.getEvolutionPolicy();
        assertEquals(217, policy.links().size());
        assertEquals(434, policy.minimumLevels().size());
        assertEquals(217, policy.minimumLevels().values().stream().filter(level -> level == 1).count());
        Map<Integer, Integer> fixed = Map.ofEntries(
                Map.entry(2, 16), Map.entry(3, 32), Map.entry(11, 7), Map.entry(12, 10),
                Map.entry(149, 55), Map.entry(248, 55), Map.entry(303, 20), Map.entry(422, 42),
                Map.entry(423, 30), Map.entry(449, 44));
        fixed.forEach((id, level) -> assertEquals(level, policy.minimumLevel(id), "Fixed threshold " + id));
        for (int id : List.of(201, 410, 417, 418, 419, 452, 453, 454, 455, 459, 460, 461)) {
            assertEquals(1, policy.minimumLevel(id), "No incoming ROM path " + id);
        }
        // Known non-level estimates provide a regression boundary for the shared BST estimator.
        for (var e : Map.of(26, 32, 65, 38, 94, 38, 212, 46, 437, 52, 442, 53).entrySet()) {
            assertEquals(e.getValue(), policy.minimumLevel(e.getKey()), "Estimated threshold " + e.getKey());
        }
        for (var link : policy.links()) {
            if (link.hasLevelThreshold()) assertEquals(link.parameter(), link.estimatedLevel());
            assertTrue(link.estimatedLevel() >= 1 && link.estimatedLevel() <= 100);
            assertTrue(policy.minimumLevel(link.to()) >= policy.minimumLevel(link.from()));
        }
        assertThrows(UnsupportedOperationException.class, () -> policy.minimumLevels().put(452, 99));
    }

    @Test void catchAllKeepsEveryChoiceInEveryModeAtLegalLevelsAcrossFortySeeds() {
        var handler = load();
        var original = handler.getEncounters(false);
        Set<Integer> physical = new HashSet<>();
        for (int seed = 0; seed < 40; seed++) {
            new WildEncounterRandomizer(handler, settings(true), new Random(seed)).randomizeEncounters();
            var after = handler.getEncounters(false);
            List<Set<Integer>> covered = List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
            List<Set<Integer>> basicRoutes = List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
            for (int a = 0; a < after.size(); a++) {
                int mask = handler.getLevelCoverageMask(ModernEmeraldEncounterData.TABLES.get(a));
                assertEquals(original.get(a).getRate(), after.get(a).getRate());
                for (int s = 0; s < after.get(a).size(); s++) {
                    var encounter = after.get(a).get(s);
                    int id = handler.getInternalId(encounter.getSpecies());
                    assertTrue(handler.getMinimumEvolutionLevel(encounter.getSpecies()) <= encounter.getLevel(),
                            "Premature " + encounter.getSpecies() + " at " + encounter.getLevel());
                    assertEquals(original.get(a).get(s).getLevel(), encounter.getLevel());
                    assertEquals(original.get(a).get(s).getMaxLevel(), encounter.getMaxLevel());
                    physical.add(id);
                    for (int mode = 0; mode < 3; mode++) if ((mask & (1 << mode)) != 0) {
                        covered.get(mode).add(ModernEmeraldSpeciesCatalog.selectionKey(id));
                    }
                    for (int mode = 0; mode < 3; mode++) {
                        if ((ModernEmeraldEncounterData.TABLES.get(a).routeCoverageMask() & (1 << mode)) != 0
                                && handler.getMinimumEvolutionLevel(encounter.getSpecies()) == 1) {
                            basicRoutes.get(mode).add(ModernEmeraldSpeciesCatalog.selectionKey(id));
                        }
                    }
                }
            }
            for (int mode = 0; mode < 3; mode++) {
                assertEquals(431, covered.get(mode).size(), "seed " + seed + " mode " + mode);
                assertEquals(214, basicRoutes.get(mode).size(), "Basic route coverage, seed " + seed + " mode " + mode);
            }
        }
        assertEquals(434, physical.size());
    }

    @Test void independentReplacementsWithoutCatchAllStillFilterTheMinimumOfEachLevelRange() {
        var handler = load();
        Set<Integer> early = new HashSet<>();
        for (int seed = 0; seed < 12; seed++) {
            new WildEncounterRandomizer(handler, settings(false), new Random(seed)).randomizeEncounters();
            for (var area : handler.getEncounters(false)) for (var encounter : area) {
                assertTrue(handler.getMinimumEvolutionLevel(encounter.getSpecies()) <= encounter.getLevel());
                if (encounter.getLevel() <= 5) early.add(handler.getInternalId(encounter.getSpecies()));
            }
        }
        assertTrue(early.containsAll(List.of(201, 410, 417, 418, 419, 452, 453, 454, 455, 459, 460, 461)));
        assertFalse(early.contains(3));
        assertFalse(early.contains(149));
    }

    @Test void pipelinePreservesEvolutionAndLevelBytesAndIndependentNativeHeadersHaveFullCoverage() throws Exception {
        var handler = load();
        var settings = settings(true);
        Path preset = temp.resolve("no-premature.rnqs");
        try (var stream = new FileOutputStream(preset.toFile())) { settings.writeToFileFormat(stream); }
        try (var stream = new FileInputStream(preset.toFile())) { settings = Settings.readFromFileFormat(stream).forModernEmerald(); }
        assertTrue(settings.isBanPrematureEvos());
        Path output = temp.resolve("legal-wild.gba");
        var result = new GameRandomizer(settings, null, handler,
                ResourceBundle.getBundle("com.uprfvx.random.gui.Bundle"), false)
                .randomize(output.toString(), new PrintStream(OutputStream.nullOutputStream()), 20261106L);
        assertTrue(result.wasSaveSuccessful(), () -> result.getException().toString());
        assertTrue(result.wasLogSuccessful(), () -> result.getLogException().toString());
        byte[] before = Files.readAllBytes(Path.of(System.getenv("MODERN_EMERALD_ROM")));
        byte[] after = Files.readAllBytes(output);
        BitSet allowed = new BitSet(before.length);
        for (var table : ModernEmeraldEncounterData.TABLES) for (int s = 0; s < table.count(); s++) {
            allowed.set(table.speciesOffset(s), table.speciesOffset(s) + 2);
            int id = ModernEmeraldStarterData.word(after, table.speciesOffset(s));
            assertTrue(handler.getEvolutionPolicy().minimumLevel(id) <= (after[table.levelOffset(s)] & 255));
        }
        allowed.set(ModernEmeraldRomHandler.OUTBREAK_MOVES_PATCH, ModernEmeraldRomHandler.OUTBREAK_MOVES_PATCH + 2);
        for (int i = 0; i < after.length; i++) if (before[i] != after[i]) {
            assertTrue(allowed.get(i), "Unrelated changed byte " + Integer.toHexString(i));
        }
        assertNativeCoverage(after);
        var reopened = new ModernEmeraldRomHandler();
        assertTrue(reopened.loadRom(output.toString()));
        Path unchanged = temp.resolve("unchanged.gba");
        assertTrue(reopened.saveRom(unchanged.toString(), 0, false));
        assertArrayEquals(after, Files.readAllBytes(unchanged));
        var repeat = load();
        Path repeated = temp.resolve("repeat.gba");
        var repeatedResult = new GameRandomizer(settings, null, repeat,
                ResourceBundle.getBundle("com.uprfvx.random.gui.Bundle"), false)
                .randomize(repeated.toString(), new PrintStream(OutputStream.nullOutputStream()), 20261106L);
        assertTrue(repeatedResult.wasSaveSuccessful());
        assertArrayEquals(after, Files.readAllBytes(repeated));
    }

    @Test void tradesIgnoreEvolutionLevelRulesAndKeepBothFormRolls() {
        var handler = load();
        Settings settings = settings(false);
        settings.setInGameTradesMod(Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED);
        // Force Gengar as the first gift; its minimum wild level is 38, but trades have no level gate.
        int gengar = 0;
        while (handler.getInternalId(handler.getWildSelectionGroups().get(gengar).getFirst()) != 94) gengar++;
        final int selected = gengar;
        var random = new Random(3) {
            boolean first = true;
            @Override public int nextInt(int bound) {
                if (first) { first = false; assertEquals(431, bound); return selected; }
                return super.nextInt(bound);
            }
        };
        new TradeRandomizer(handler, settings, random).randomizeIngameTrades();
        assertEquals(94, handler.getInternalId(handler.getInGameTrades().getFirst().getGivenSpecies()));
        new TradeRandomizer(handler, settings, new Random(78)).randomizeIngameTrades();
        var limited = handler.getInGameTrades();
        settings.setBanPrematureEvos(false);
        new TradeRandomizer(handler, settings, new Random(78)).randomizeIngameTrades();
        assertEquals(limited, handler.getInGameTrades());
    }

    /** Reads the saved native header list rather than the adapter's coverage masks. */
    static void assertNativeCoverage(byte[] image) {
        Set<Integer> evolved = ModernEmeraldStarterData.evolvedSpecies(image);
        Set<Integer> basics = new HashSet<>();
        for (var group : ModernEmeraldSpeciesCatalog.selectionGroups()) {
            if (group.stream().anyMatch(id -> !evolved.contains(id))) basics.add(ModernEmeraldSpeciesCatalog.selectionKey(group.getFirst()));
        }
        for (int mode = 0; mode < 3; mode++) {
            Set<Integer> seen = new HashSet<>();
            Set<Integer> routes = new HashSet<>();
            for (int base = 0xBDD84C; (image[base] & 255) != 255;) {
                int next = base + 20;
                while (image[next] == image[base] && image[next + 1] == image[base + 1]) next += 20;
                int count = (next - base) / 20;
                if (count == 2 || count == 3) {
                    int selected = base + Math.min(mode, count - 1) * 20;
                    int[] slots = {12, 5, 5, 10};
                    for (int type = 0; type < 4; type++) {
                        int pointer = word32(image, selected + 4 + type * 4);
                        if (pointer == 0) continue;
                        int info = pointer - 0x08000000;
                        if (image[info] == 0) continue;
                        int data = word32(image, info + 4) - 0x08000000;
                        for (int s = 0; s < slots[type]; s++) {
                            int id = ModernEmeraldStarterData.word(image, data + s * 4 + 2);
                            seen.add(ModernEmeraldSpeciesCatalog.selectionKey(id));
                            if (image[base] == 0 && (image[base + 1] & 255) >= 16 && (image[base + 1] & 255) <= 49) {
                                routes.add(ModernEmeraldSpeciesCatalog.selectionKey(id));
                            }
                        }
                    }
                }
                base = next;
            }
            assertEquals(431, seen.size(), "Native view " + mode);
            assertTrue(routes.containsAll(basics), "Native basic route coverage, view " + mode);
            assertTrue(seen.containsAll(List.of(149, 248, 437, 442, 452, 453, 454, 455, 459, 460, 461)));
        }
    }
    private static int word32(byte[] image, int offset) {
        return (image[offset] & 255) | ((image[offset + 1] & 255) << 8)
                | ((image[offset + 2] & 255) << 16) | ((image[offset + 3] & 255) << 24);
    }
}
