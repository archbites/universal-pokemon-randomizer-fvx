package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.random.exceptions.RandomizationException;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.romhandlers.ModernEmeraldRomHandler;

import java.util.*;

/** The same choice groups as wild encounters, with a second roll for Deoxys/Unown forms. */
final class ModernEmeraldTradeRandomizer {
    private ModernEmeraldTradeRandomizer() {}

    static void randomize(ModernEmeraldRomHandler handler, Settings settings, Random random, List<String> names) {
        if (settings.getInGameTradesMod() != Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED) {
            throw new UnsupportedOperationException("Modern Emerald supports randomizing both sides of NPC trades");
        }
        var trades = handler.getInGameTrades();
        var givenGroups = new ArrayList<>(handler.getWildSelectionGroups());
        var requestGroups = new ArrayList<>(handler.getWildSelectionGroups());
        var nicknames = new ArrayList<>(names.stream().filter(handler::isTradeNicknameValid).distinct().toList());
        if (settings.isRandomizeInGameTradesNicknames() && nicknames.size() < trades.size()) {
            throw new RandomizationException("At least four distinct 1-10 letter trade nicknames are required.");
        }
        for (var trade : trades) {
            var givenForms = givenGroups.remove(random.nextInt(givenGroups.size()));
            Species given = givenForms.get(random.nextInt(givenForms.size()));
            trade.getGivenSpeciesHolder().setSpecies(given);
            if (handler.getInternalId(given) == 201) trade.getGivenSpeciesHolder().setFormeNumber(random.nextInt(28));

            // The game displays the same name for several functional forms. Avoid a trade whose
            // request can be satisfied by its own gift, even if they came from different choice groups.
            var eligibleRequests = requestGroups.stream()
                    .filter(group -> !group.getFirst().getName().equals(given.getName())).toList();
            var requestForms = eligibleRequests.get(random.nextInt(eligibleRequests.size()));
            requestGroups.remove(requestForms);
            trade.setRequestedSpecies(requestForms.get(random.nextInt(requestForms.size())));
            if (settings.isRandomizeInGameTradesNicknames()) {
                trade.setNickname(nicknames.remove(random.nextInt(nicknames.size())));
            }
            if (settings.isRandomizeInGameTradesIVs()) {
                for (int stat = 0; stat < 6; stat++) trade.getIVs()[stat] = random.nextInt(32);
            }
        }
        handler.setInGameTrades(trades);
    }
}
