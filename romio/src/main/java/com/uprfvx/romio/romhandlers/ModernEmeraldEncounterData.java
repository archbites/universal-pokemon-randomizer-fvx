package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.gamedata.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntFunction;

/** Audited 3.6 physical tables. Aliased tables have one binding and one randomization per physical slot. */
public final class ModernEmeraldEncounterData {
    private ModernEmeraldEncounterData() {}

    public record Table(int offset, int count, int rate, int mapIndex, int routeCoverageMask,
                        EncounterType type, String name, boolean outbreak) {
        public int speciesOffset(int slot) { return offset + slot * (outbreak ? 12 : 4) + (outbreak ? 0 : 2); }
        public int levelOffset(int slot) { return offset + slot * (outbreak ? 12 : 4) + (outbreak ? 10 : 0); }
        public int maxLevelOffset(int slot) { return levelOffset(slot) + (outbreak ? 0 : 1); }
    }
    public static final List<Table> TABLES = loadTables();

    /** Normal original/day/night headers, including late-game caves. Excludes single-header special
     * maps and Altering Cave's nine independently selected sets from coverage promises. */
    public static Map<Integer, Integer> levelCoverageMasks(byte[] image) {
        Map<Integer, Integer> masks = new HashMap<>();
        Set<Integer> known = new HashSet<>();
        TABLES.stream().filter(t -> !t.outbreak()).forEach(t -> known.add(t.offset()));
        for (int header = 0xBDD84C; (image[header] & 255) != 255;) {
            int end = header + 20;
            while (image[end] == image[header] && image[end + 1] == image[header + 1]) end += 20;
            int count = (end - header) / 20;
            if (count == 2 || count == 3) {
                for (int mode = 0; mode < 3; mode++) {
                    int selected = header + Math.min(mode, count - 1) * 20;
                    for (int type = 0; type < 4; type++) {
                        int pointer = word32(image, selected + 4 + type * 4);
                        if (pointer == 0) continue;
                        int info = pointer - 0x08000000;
                        if (image[info] == 0) continue;
                        int data = word32(image, info + 4) - 0x08000000;
                        if (known.contains(data)) masks.merge(data, 1 << mode, (a, b) -> a | b);
                    }
                }
            }
            header = end;
        }
        return Map.copyOf(masks);
    }

    private static int word32(byte[] image, int offset) {
        return (image[offset] & 255) | ((image[offset + 1] & 255) << 8)
                | ((image[offset + 2] & 255) << 16) | ((image[offset + 3] & 255) << 24);
    }

    private static List<Table> loadTables() {
        var types = List.of(EncounterType.WALKING, EncounterType.SURFING, EncounterType.INTERACT,
                EncounterType.FISHING, EncounterType.SPECIAL, EncounterType.SPECIAL);
        List<Table> tables = new ArrayList<>();
        String path = "/com/uprfvx/romio/romentries/modernemerald_36_encounters.tsv";
        try (var scanner = new Scanner(Objects.requireNonNull(
                ModernEmeraldEncounterData.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] columns = line.split("\t");
                tables.add(new Table(Integer.parseInt(columns[0], 16), Integer.parseInt(columns[1]),
                        Integer.parseInt(columns[2]), Integer.parseInt(columns[3]), Integer.parseInt(columns[4]),
                        types.get(Integer.parseInt(columns[5])), columns[6], columns[5].equals("5")));
            }
        }
        return List.copyOf(tables);
    }

    public static List<EncounterArea> read(byte[] image, IntFunction<Species> speciesByInternal) {
        List<EncounterArea> areas = new ArrayList<>();
        for (Table table : TABLES) {
            EncounterArea area = new EncounterArea();
            area.setIdentifiers(table.name(), table.mapIndex(), table.type(), table.name());
            area.setRate(table.rate());
            for (int i = 0; i < table.count(); i++) {
                int offset = table.speciesOffset(i);
                int internal = (image[offset] & 255) | ((image[offset + 1] & 255) << 8);
                Species mon = Objects.requireNonNull(speciesByInternal.apply(internal),
                        "Unknown species in " + table.name());
                Encounter encounter = new Encounter(mon.getBaseForme(), image[table.levelOffset(i)] & 255);
                encounter.getSpeciesHolder().setAltFormeAllowed();
                encounter.getSpeciesHolder().setSpecies(mon);
                encounter.setMaxLevel(image[table.maxLevelOffset(i)] & 255);
                area.add(encounter);
            }
            areas.add(area);
        }
        return areas;
    }

    public static List<EncounterArea> copy(List<EncounterArea> original) {
        List<EncounterArea> areas = new ArrayList<>();
        for (EncounterArea source : original) {
            EncounterArea area = new EncounterArea();
            area.setIdentifiers(source.getDisplayName(), source.getMapIndex(), source.getEncounterType(),
                    source.getLocationTag());
            area.setRate(source.getRate());
            for (Encounter old : source) {
                Encounter encounter = new Encounter(old.getSpecies().getBaseForme(), old.getLevel());
                encounter.getSpeciesHolder().setAltFormeAllowed();
                encounter.getSpeciesHolder().setSpecies(old.getSpecies());
                encounter.setMaxLevel(old.getMaxLevel());
                area.add(encounter);
            }
            areas.add(area);
        }
        return areas;
    }
}
