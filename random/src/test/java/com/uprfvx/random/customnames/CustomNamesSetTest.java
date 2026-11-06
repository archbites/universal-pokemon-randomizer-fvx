package com.uprfvx.random.customnames;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomNamesSetTest {
    @Test void bundledNicknamePoolContainsExactlyOneThousandDistinctCompleteGbaNames() throws Exception {
        List<String> names;
        try (var stream = getClass().getResourceAsStream("/data/trainer_and_mon_names/PokemonNicknames.txt")) {
            assertNotNull(stream);
            names = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().toList();
        }
        assertEquals(1000, names.size());
        assertEquals(1000, names.stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().count());
        for (String name : names) assertTrue(name.matches("[A-Za-z]{1,10}"), name);
        for (String old : List.of("Red", "Blue", "Yellow", "Cuddles", "Angry", "Scout", "Buster", "Rusty",
                "Shadow", "Gizmo", "Chester", "Sylvester", "Merlin", "Smokey", "Angel", "Midnight", "Willow",
                "Baxter", "Cookie", "Callie")) assertFalse(names.contains(old), old);
        assertTrue(names.containsAll(List.of("Obsidian", "Requiem", "Monsoon", "Denali")));
        // A standalone build can load bundled defaults without a /data directory in its working directory.
        var loaded = CustomNamesSet.readNamesFromFile();
        assertFalse(loaded.pokemonNicknames().isEmpty());
        assertFalse(loaded.trainerNames().isEmpty());
    }
}
