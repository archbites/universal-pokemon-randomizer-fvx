package com.uprfvx.romio.romhandlers;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** All 30 active Brendan/May starter slots, verified against matching 3.6 party symbols and trainer pointers. */
public final class ModernEmeraldRivalData {
    private ModernEmeraldRivalData() {}
    public record Binding(int speciesOffset, int levelOffset, int starterIndex, int customFlagOffset, String name) {}
    public static final List<Binding> BINDINGS = load();
    private static List<Binding> load() {
        List<Binding> result = new ArrayList<>();
        String path = "/com/uprfvx/romio/romentries/modernemerald_36_rivals.tsv";
        try (var scanner = new Scanner(Objects.requireNonNull(ModernEmeraldRivalData.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] c = line.split("\t");
                result.add(new Binding(Integer.parseInt(c[0], 16), Integer.parseInt(c[1], 16),
                        Integer.parseInt(c[2]), Integer.parseInt(c[3], 16), c[4]));
            }
        }
        if (result.size() != 30) throw new IllegalStateException("Incomplete Modern Emerald rival bindings");
        return List.copyOf(result);
    }
}
