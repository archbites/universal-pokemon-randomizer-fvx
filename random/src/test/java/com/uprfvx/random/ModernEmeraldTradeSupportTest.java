package com.uprfvx.random;

import com.uprfvx.random.customnames.CustomNamesSet;
import com.uprfvx.random.randomizers.TradeRandomizer;
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
class ModernEmeraldTradeSupportTest {
    @TempDir Path temp;

    private ModernEmeraldRomHandler load() {
        var handler = new ModernEmeraldRomHandler();
        assertTrue(handler.loadRom(System.getenv("MODERN_EMERALD_ROM")));
        return handler;
    }

    private Settings settings(boolean nicknames, boolean ivs) {
        var settings = new Settings();
        settings.setRomName("Pokemon Modern Emerald 3.6");
        settings.setInGameTradesMod(Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED);
        settings.setRandomizeInGameTradesNicknames(nicknames);
        settings.setRandomizeInGameTradesIVs(ivs);
        return settings;
    }

    @Test void readsExactlyFourExpandedRecordsAndReturnsDetachedCopies() {
        var handler = load();
        var trades = handler.getInGameTrades();
        assertEquals(List.of(298, 353, 116, 52), trades.stream().map(t -> handler.getInternalId(t.getGivenSpecies())).toList());
        assertEquals(List.of(392, 386, 395, 315), trades.stream().map(t -> handler.getInternalId(t.getRequestedSpecies())).toList());
        assertEquals(List.of("Dots", "Pluses", "Seasor", "Meowow"), trades.stream().map(InGameTrade::getNickname).toList());
        assertArrayEquals(new int[]{5, 4, 5, 4, 4, 4}, trades.getFirst().getIVs());
        trades.getFirst().getIVs()[0] = 31;
        trades.getFirst().setNickname("Changed");
        assertEquals("Dots", handler.getInGameTrades().getFirst().getNickname());
        assertEquals(5, handler.getInGameTrades().getFirst().getIVs()[0]);
    }

    @Test void randomizesAllRealChoicesWithSharedFormWeightingAndOptionalFields() throws Exception {
        var handler = load();
        var original = handler.getInGameTrades();
        Set<Integer> givenIds = new HashSet<>(), requestedIds = new HashSet<>(), letters = new HashSet<>();
        var random = new Random(20261106);
        for (int sample = 0; sample < 4000; sample++) {
            new TradeRandomizer(handler, settings(false, false), random).randomizeIngameTrades();
            Set<Integer> givenKeys = new HashSet<>(), requestKeys = new HashSet<>();
            int index = 0;
            for (var trade : handler.getInGameTrades()) {
                int given = handler.getInternalId(trade.getGivenSpecies());
                int requested = handler.getInternalId(trade.getRequestedSpecies());
                assertTrue(ModernEmeraldSpeciesCatalog.isEligible(given));
                assertTrue(ModernEmeraldSpeciesCatalog.isEligible(requested));
                assertTrue(givenKeys.add(ModernEmeraldSpeciesCatalog.selectionKey(given)));
                assertTrue(requestKeys.add(ModernEmeraldSpeciesCatalog.selectionKey(requested)));
                assertNotEquals(trade.getRequestedSpecies().getName(), trade.getGivenSpecies().getName());
                givenIds.add(given);
                requestedIds.add(requested);
                if (given == 201) letters.add(trade.getGivenSpeciesHolder().getFormeNumber());
                assertEquals(original.get(index).getNickname(), trade.getNickname());
                assertArrayEquals(original.get(index++).getIVs(), trade.getIVs());
            }
        }
        for (int id : List.of(201, 410, 417, 418, 419, 453, 454, 459, 460, 461)) {
            assertTrue(givenIds.contains(id), "Given " + id);
            assertTrue(requestedIds.contains(id), "Requested " + id);
        }
        assertTrue(letters.size() > 4);
        new TradeRandomizer(handler, settings(true, true), new Random(123)).randomizeIngameTrades();
        var names = CustomNamesSet.readNamesFromFile().pokemonNicknames();
        Set<String> used = new HashSet<>();
        for (var trade : handler.getInGameTrades()) {
            assertTrue(names.contains(trade.getNickname()));
            assertTrue(used.add(trade.getNickname()));
            assertTrue(handler.isTradeNicknameValid(trade.getNickname()));
            assertTrue(Arrays.stream(trade.getIVs()).allMatch(iv -> iv >= 0 && iv <= 31));
        }
    }

    @Test void tradeOnlyPipelineHasExactByteBoundariesPreservesOverridesAndReloads() throws Exception {
        var handler = load();
        byte[] before = Files.readAllBytes(Path.of(System.getenv("MODERN_EMERALD_ROM")));
        Path output = temp.resolve("trades.gba");
        var settings = settings(true, true);
        settings.setRandomizeInGameTradesOTs(true); // Imported unsupported options cannot escape the whitelist.
        settings.setRandomizeInGameTradesItems(true);
        var result = new GameRandomizer(settings, null, handler,
                ResourceBundle.getBundle("com.uprfvx.random.gui.Bundle"), false)
                .randomize(output.toString(), new PrintStream(OutputStream.nullOutputStream()), 20261106L);
        assertTrue(result.wasSaveSuccessful(), () -> result.getException().toString());
        assertTrue(result.wasLogSuccessful(), () -> result.getLogException().toString());
        byte[] after = Files.readAllBytes(output);
        BitSet allowed = new BitSet(before.length);
        for (int i = 0; i < 4; i++) {
            int o = ModernEmeraldTradeData.offset(i);
            allowed.set(o, o + 11);
            allowed.set(o + 12, o + 14);
            allowed.set(o + 20, o + 27);
            allowed.set(o + 60, o + 62);
            if (handler.getInternalId(handler.getInGameTrades().get(i).getGivenSpecies()) == 201) allowed.set(o + 40, o + 44);
            assertArrayEquals(Arrays.copyOfRange(before, o + 14, o + 20), Arrays.copyOfRange(after, o + 14, o + 20));
            assertArrayEquals(Arrays.copyOfRange(before, o + 28, o + 40), Arrays.copyOfRange(after, o + 28, o + 40));
            assertArrayEquals(Arrays.copyOfRange(before, o + 44, o + 60), Arrays.copyOfRange(after, o + 44, o + 60));
        }
        allowed.set(ModernEmeraldTradeData.REQUEST_SPECIAL_POINTER, ModernEmeraldTradeData.REQUEST_SPECIAL_POINTER + 4);
        allowed.set(ModernEmeraldTradeData.REQUEST_ROUTINE, ModernEmeraldTradeData.REQUEST_ROUTINE + ModernEmeraldTradeData.requestCode().length);
        for (var text : ModernEmeraldTradeData.FOLLOW_UP_TEXT) allowed.set(text.offset(), text.offset() + text.length());
        int changed = 0;
        for (int offset = 0; offset < before.length; offset++) if (before[offset] != after[offset]) {
            assertTrue(allowed.get(offset), "Unrelated changed byte " + Integer.toHexString(offset));
            changed++;
        }
        assertTrue(changed > 100);
        assertTrue(ModernEmeraldTradeData.validRequestPatch(after));
        assertTrue(ModernEmeraldRomHandler.isSupportedImage(after));
        var reopened = new ModernEmeraldRomHandler();
        assertTrue(reopened.loadRom(output.toString()));
        assertEquals(handler.getInGameTrades(), reopened.getInGameTrades());
        Path unchanged = temp.resolve("unchanged.gba");
        assertTrue(reopened.saveRom(unchanged.toString(), 0, false));
        assertArrayEquals(after, Files.readAllBytes(unchanged));
        after[ModernEmeraldTradeData.REQUEST_ROUTINE] ^= 1;
        assertFalse(ModernEmeraldRomHandler.isSupportedImage(after));
    }

    @Test void functionalFormsAndEveryUnownLetterSurviveNativeStorageWithoutChangingNatureOrShininess() throws Exception {
        var handler = load();
        var trades = handler.getInGameTrades();
        trades.get(0).getGivenSpeciesHolder().setSpecies(handler.getSpeciesForInternalId(201));
        trades.get(0).getGivenSpeciesHolder().setFormeNumber(27);
        trades.get(0).setRequestedSpecies(handler.getSpeciesForInternalId(417));
        trades.get(1).getGivenSpeciesHolder().setSpecies(handler.getSpeciesForInternalId(419));
        trades.get(1).setRequestedSpecies(handler.getSpeciesForInternalId(454));
        trades.get(2).getGivenSpeciesHolder().setSpecies(handler.getSpeciesForInternalId(454));
        trades.get(2).setRequestedSpecies(handler.getSpeciesForInternalId(459));
        handler.setInGameTrades(trades);
        Path output = temp.resolve("forms.gba");
        assertTrue(handler.saveRom(output.toString(), 0, false));
        var reopened = new ModernEmeraldRomHandler();
        assertTrue(reopened.loadRom(output.toString()));
        assertEquals(trades, reopened.getInGameTrades());
        byte[] image = Files.readAllBytes(output);
        assertEquals(0, image[ModernEmeraldTradeData.TABLE + 26]); // Unown cannot use Seedot's forced second ability.
        for (long original : new long[]{132, 111, 127, 139, 0xFFFFFFFFL}) {
            for (long ot : new long[]{0, original, 0x12345678L}) {
                for (int letter = 0; letter < 28; letter++) {
                    long pid = ModernEmeraldTradeData.unownPersonality(original, ot, letter);
                    assertEquals(letter, ModernEmeraldTradeData.unownLetter(pid));
                    assertEquals(original % 25, pid % 25);
                    assertEquals(shiny(original, ot), shiny(pid, ot));
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new InGameTrade(handler.getSpeciesForInternalId(417), handler.getSpeciesForInternalId(1)));
    }

    private static boolean shiny(long personality, long ot) {
        return ((personality >>> 16) ^ (personality & 65535) ^ (ot >>> 16) ^ (ot & 65535)) < 8;
    }

    @Test void combinesWithWildAndStartersAndStripsUnsupportedTradeOptions() throws Exception {
        var handler = load();
        var settings = settings(true, true);
        settings.setStartersMod(Settings.StartersMod.RANDOM_BASIC);
        settings.setRandomizeWildPokemon(true);
        settings.setCatchEmAllEncounters(true);
        var safe = settings.forModernEmerald();
        assertEquals(Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED, safe.getInGameTradesMod());
        assertTrue(safe.isRandomizeInGameTradesIVs());
        Path output = temp.resolve("combined.gba");
        var result = new GameRandomizer(settings, null, handler,
                ResourceBundle.getBundle("com.uprfvx.random.gui.Bundle"), false)
                .randomize(output.toString(), new PrintStream(OutputStream.nullOutputStream()), 42);
        assertTrue(result.wasSaveSuccessful(), () -> result.getException().toString());
        assertTrue(result.wasLogSuccessful(), () -> result.getLogException().toString());
        ModernEmeraldWildSupportTest.assertNativeRouteCoverage(Files.readAllBytes(output));
        var reopened = new ModernEmeraldRomHandler();
        assertTrue(reopened.loadRom(output.toString()));
        assertEquals(handler.getInGameTrades(), reopened.getInGameTrades());
        settings.setInGameTradesMod(Settings.InGameTradesMod.RANDOMIZE_GIVEN);
        assertEquals(Settings.InGameTradesMod.UNCHANGED, settings.forModernEmerald().getInGameTradesMod());
        assertFalse(settings.forModernEmerald().isRandomizeInGameTradesNicknames());
        assertFalse(settings.forModernEmerald().isRandomizeInGameTradesIVs());
        assertThrows(UnsupportedOperationException.class, () -> new TradeRandomizer(handler, settings, new Random(0)).randomizeIngameTrades());
    }
}
