package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.gamedata.basestats.BaseStats;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Deliberately narrow support for the supplied Modern Emerald 3.6 release.
 * Never runs Gen3's map preprocessing, free-space reclamation, or unconditional vanilla writers.
 * Only verified move fields, wild slots, basic starters and NPC trades are writable.
 */
public class ModernEmeraldRomHandler extends Gen3RomHandler {
    public static final int MOVE_DATA = 0x8E0898;
    public static final int MOVE_COUNT = 374;
    public static final int MOVE_SIZE = 12;
    private static final int MOVE_NAMES = 0x6E88F4;
    private static final int SPECIES_NAMES = 0x6E7518;
    private static final int SPECIES_INFO = 0x8E5960;
    public static final int OUTBREAK_MOVES_PATCH = 0x21CA78;
    private static final String FINGERPRINT =
            "096c81b4884864f64a56a01e8cadeead64b86fb8ba4c4e9d6ccd1a1a1ea514c0";
    private static final int[] WRITABLE_OFFSETS = buildWritableOffsets();

    private static int[] buildWritableOffsets() {
        SortedSet<Integer> offsets = new TreeSet<>();
        for (int move = 1; move <= MOVE_COUNT; move++) {
            if (move == 165) continue;
            offsets.add(MOVE_DATA + move * MOVE_SIZE + 3);
            offsets.add(MOVE_DATA + move * MOVE_SIZE + 4);
        }
        for (var table : ModernEmeraldEncounterData.TABLES) {
            for (int slot = 0; slot < table.count(); slot++) {
                offsets.add(table.speciesOffset(slot));
                offsets.add(table.speciesOffset(slot) + 1);
            }
        }
        offsets.add(OUTBREAK_MOVES_PATCH);
        offsets.add(OUTBREAK_MOVES_PATCH + 1);
        for (int i = 0; i < 6; i++) offsets.add(ModernEmeraldStarterData.STARTERS + i);
        for (var text : ModernEmeraldStarterData.TYPE_EXAMPLES) {
            for (int i = 13; i < text.length(); i++) offsets.add(text.offset() + i);
        }
        for (int trade = 0; trade < ModernEmeraldTradeData.COUNT; trade++) {
            int offset = ModernEmeraldTradeData.offset(trade);
            for (int i = 0; i < 11; i++) offsets.add(offset + i);
            for (int i : new int[]{12, 13, 20, 21, 22, 23, 24, 25, 26, 40, 41, 42, 43, 60, 61}) offsets.add(offset + i);
        }
        for (var text : ModernEmeraldTradeData.FOLLOW_UP_TEXT) {
            for (int i = 0; i < text.length(); i++) offsets.add(text.offset() + i);
        }
        for (int i = 0; i < 4; i++) offsets.add(ModernEmeraldTradeData.REQUEST_SPECIAL_POINTER + i);
        for (int i = 0; i < ModernEmeraldTradeData.requestCode().length; i++) offsets.add(ModernEmeraldTradeData.REQUEST_ROUTINE + i);
        return offsets.stream().mapToInt(Integer::intValue).toArray();
    }

    public static int[] writableOffsets() { return WRITABLE_OFFSETS.clone(); }

    public static class Factory extends RomHandler.Factory {
        @Override public ModernEmeraldRomHandler create() { return new ModernEmeraldRomHandler(); }
        @Override public boolean isLoadable(String filename) {
            return new java.io.File(filename).length() == Gen3Constants.size32M
                    && hasExpandedEmeraldHeader(loadFilePartial(filename, 0x200))
                    && isSupportedImage(loadFile(filename));
        }
    }

    private final List<Species> species = new ArrayList<>();
    private final Map<Integer, Species> speciesByInternal = new HashMap<>();
    private final Map<Species, Integer> internalBySpecies = new HashMap<>();
    private final List<Species> speciesWithFormes = new ArrayList<>();
    private List<EncounterArea> modernEncounters;
    private boolean wildRandomized;
    private List<Move> modernMoves;
    private List<Species> modernStarters;
    private boolean startersChanged;
    private Set<Integer> evolvedSpecies;
    private ModernEmeraldEvolutionData.Policy evolutionPolicy;
    private Map<Integer, Integer> levelCoverageMasks;
    private List<InGameTrade> modernTrades;
    private boolean tradesChanged;

    /** Public GF header is preserved by the decomp; its expanded dex distinguishes it from retail. */
    static boolean hasExpandedEmeraldHeader(byte[] image) {
        return image.length >= 0x1D0
                && new String(image, 0xAC, 4, StandardCharsets.US_ASCII).equals("BPEE")
                && ((image[0x170] & 255) | ((image[0x171] & 255) << 8)) > 386;
    }

    public static boolean isSupportedImage(byte[] image) {
        if (image.length != Gen3Constants.size32M || !hasExpandedEmeraldHeader(image)) return false;
        try {
            // Accept our own output, while pinning every other byte to this exact release.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int start = 0;
            for (int offset : WRITABLE_OFFSETS) {
                digest.update(image, start, offset - start);
                digest.update((byte) 0);
                start = offset + 1;
            }
            digest.update(image, start, image.length - start);
            if (!HexFormat.of().formatHex(digest.digest()).equals(FINGERPRINT)) return false;
            if (!ModernEmeraldTradeData.validRequestPatch(image)) return false;
            // Only the original LDR or our branch to the existing success epilogue is allowed here.
            if ((image[OUTBREAK_MOVES_PATCH] & 255) != 0x0B
                    || ((image[OUTBREAK_MOVES_PATCH + 1] & 255) != 0x4E
                    && (image[OUTBREAK_MOVES_PATCH + 1] & 255) != 0xE0)) return false;
            for (var table : ModernEmeraldEncounterData.TABLES) {
                for (int slot = 0; slot < table.count(); slot++) {
                    int offset = table.speciesOffset(slot);
                    int internal = (image[offset] & 255) | ((image[offset + 1] & 255) << 8);
                    if (!ModernEmeraldSpeciesCatalog.isEligible(internal)) return false;
                }
            }
            Set<Integer> evolved = ModernEmeraldStarterData.evolvedSpecies(image);
            Set<Integer> choices = new HashSet<>();
            for (int slot = 0; slot < 3; slot++) {
                int internal = ModernEmeraldStarterData.word(image, ModernEmeraldStarterData.STARTERS + slot * 2);
                if (!ModernEmeraldSpeciesCatalog.isEligible(internal) || evolved.contains(internal)
                        || !choices.add(ModernEmeraldSpeciesCatalog.selectionKey(internal))) return false;
            }
            for (int trade = 0; trade < ModernEmeraldTradeData.COUNT; trade++) {
                int offset = ModernEmeraldTradeData.offset(trade);
                if (!ModernEmeraldSpeciesCatalog.isEligible(ModernEmeraldStarterData.word(image, offset + 12))
                        || !ModernEmeraldSpeciesCatalog.isEligible(ModernEmeraldStarterData.word(image, offset + 60))) return false;
                for (int i = 20; i < 26; i++) if ((image[offset + i] & 255) > 31) return false;
                if ((image[offset + 26] & 255) > 1) return false;
                boolean terminated = false;
                for (int i = 0; i < 11; i++) {
                    int character = image[offset + i] & 255;
                    if (character == 255) { terminated = i > 0; break; }
                    if (character < 0xBB || character > 0xEE) return false;
                }
                if (!terminated) return false;
            }
            return true;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public boolean detectRom(byte[] image) { return isSupportedImage(image); }

    @Override public void midLoadingSetUp() {
        initRomEntry();
        initTextTables();
    }

    @Override protected void initRomEntry() {
        romEntry = Gen3RomEntry.READER.readFromString("""
                [Modern Emerald 3.6]
                Game=BPEE
                Version=0
                Type=Em
                TableFile=gba_english
                CRC32=263A1187
                """).getFirst();
    }

    @Override protected void loadGameData() {
        loadSpeciesStats();
        loadMoves();
        evolvedSpecies = ModernEmeraldStarterData.evolvedSpecies(rom);
        evolutionPolicy = ModernEmeraldEvolutionData.read(rom);
        levelCoverageMasks = ModernEmeraldEncounterData.levelCoverageMasks(rom);
        modernStarters = new ArrayList<>();
        for (int slot = 0; slot < 3; slot++) modernStarters.add(speciesByInternal.get(
                readWord(ModernEmeraldStarterData.STARTERS + slot * 2)));
        startersChanged = false;
        modernEncounters = ModernEmeraldEncounterData.read(rom, speciesByInternal::get);
        wildRandomized = false;
        loadTrades();
        tradesChanged = false;
        trainers.clear();
        trainerClasses.clear();
    }

    @Override public void loadSpeciesStats() {
        species.clear();
        species.add(null);
        speciesByInternal.clear();
        internalBySpecies.clear();
        speciesWithFormes.clear();
        String path = "/com/uprfvx/romio/romentries/modernemerald_36_species.tsv";
        try (var scanner = new Scanner(Objects.requireNonNull(getClass().getResourceAsStream(path)),
                StandardCharsets.UTF_8)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] columns = line.split("\t");
                int internal = Integer.parseInt(columns[0]);
                Species mon = readSpecies(internal, Integer.parseInt(columns[1]));
                mon.setGeneration(mon.getNumber() <= 151 ? 1 : mon.getNumber() <= 251 ? 2
                        : mon.getNumber() <= 386 ? 3 : mon.getNumber() <= 493 ? 4
                        : mon.getNumber() <= 649 ? 5 : mon.getNumber() <= 721 ? 6
                        : mon.getNumber() <= 809 ? 7 : mon.getNumber() <= 905 ? 8 : 9);
                species.add(mon);
                speciesByInternal.put(internal, mon);
                internalBySpecies.put(mon, internal);
            }
        }
        speciesWithFormes.addAll(species);
        for (var form : ModernEmeraldSpeciesCatalog.FORMS) {
            // As in other handlers, stat-bearing forms need unique object numbers; getBaseNumber is the dex ID.
            Species mon = readSpecies(form.internalId(), 2000 + form.internalId());
            mon.setFormeSuffix(form.suffix());
            mon.setGeneration(form.generation());
            speciesByInternal.get(form.baseInternalId()).addAltForme(form.formNumber(), mon);
            speciesByInternal.put(form.internalId(), mon);
            internalBySpecies.put(mon, form.internalId());
            speciesWithFormes.add(mon);
        }
        // Letter forms are real appearances but are encoded in personality, not in the encounter species word.
        for (int letter = 1; letter < 28; letter++) speciesByInternal.get(201).addCosmeticAltForme(letter);
        if (speciesByInternal.size() != 434) throw new IllegalStateException("Incomplete Modern Emerald roster");
    }

    private Species readSpecies(int internal, int number) {
        Species mon = new Species(number);
        mon.setName(readFixedLengthString(SPECIES_NAMES + internal * 11, 11));
        int offset = SPECIES_INFO + internal * 40;
        mon.setBaseStats(new BaseStats(rom[offset] & 255, rom[offset + 1] & 255,
                rom[offset + 2] & 255, rom[offset + 4] & 255, rom[offset + 5] & 255, rom[offset + 3] & 255));
        mon.setPrimaryType(modernType(rom[offset + 6] & 255));
        Type secondary = modernType(rom[offset + 7] & 255);
        mon.setSecondaryType(secondary == mon.getPrimaryType(false) ? null : secondary);
        mon.setCatchRate(rom[offset + 8] & 255);
        mon.setGenderRatio(rom[offset + 16] & 255);
        mon.setGrowthCurve(ExpCurve.fromByte(rom[offset + 19]));
        mon.setAbility1(rom[offset + 22] & 255);
        mon.setAbility2(rom[offset + 23] & 255);
        return mon;
    }

    public Species getSpeciesForInternalId(int id) { return speciesByInternal.get(id); }
    public int getInternalId(Species mon) {
        Integer id = internalBySpecies.get(mon);
        if (id == null) throw new IllegalArgumentException("Unknown Modern Emerald species: " + mon);
        return id;
    }

    public List<List<Species>> getWildSelectionGroups() {
        return ModernEmeraldSpeciesCatalog.selectionGroups().stream()
                .map(group -> group.stream().map(speciesByInternal::get).toList()).toList();
    }

    public List<List<Species>> getBasicStarterSelectionGroups() {
        return getWildSelectionGroups().stream()
                .map(group -> group.stream().filter(mon -> !evolvedSpecies.contains(getInternalId(mon))).toList())
                .filter(group -> !group.isEmpty()).toList();
    }

    public ModernEmeraldEvolutionData.Policy getEvolutionPolicy() { return evolutionPolicy; }
    public int getMinimumEvolutionLevel(Species mon) { return evolutionPolicy.minimumLevel(getInternalId(mon)); }
    public int getLevelCoverageMask(ModernEmeraldEncounterData.Table table) {
        return levelCoverageMasks.getOrDefault(table.offset(), 0);
    }

    private static Type modernType(int id) {
        return id == 18 ? Type.FAIRY : Gen3Constants.typeTable[id];
    }

    @Override public void loadMoves() {
        modernMoves = new ArrayList<>(Collections.nCopies(MOVE_COUNT + 1, null));
        for (int id = 1; id <= MOVE_COUNT; id++) {
            int offset = MOVE_DATA + id * MOVE_SIZE;
            Move move = new Move();
            move.number = move.internalId = id;
            move.name = readFixedLengthString(MOVE_NAMES + id * 13, 13);
            move.effectIndex = rom[offset] & 255;
            move.power = rom[offset + 1] & 255;
            move.type = modernType(rom[offset + 2] & 255);
            move.hitratio = rom[offset + 3] & 255;
            move.pp = rom[offset + 4] & 255;
            move.category = switch (rom[offset + 9]) {
                case 0 -> MoveCategory.PHYSICAL;
                case 1 -> MoveCategory.SPECIAL;
                default -> MoveCategory.STATUS;
            };
            modernMoves.set(id, move);
        }
    }

    @Override public void saveMoves() {
        for (int id = 1; id <= MOVE_COUNT; id++) {
            if (id == 165) continue;
            Move move = modernMoves.get(id);
            int offset = MOVE_DATA + id * MOVE_SIZE;
            rom[offset + 3] = (byte) Math.clamp(Math.round(move.hitratio), 0, 100);
            rom[offset + 4] = (byte) Math.clamp(move.pp, 0, 255);
        }
    }

    @Override protected void prepareSaveRom() {
        // Discard unsupported direct writes. Flush only whitelisted fields from the loaded data model.
        rom = originalRom.clone();
        saveMoves();
        for (int area = 0; area < modernEncounters.size(); area++) {
            var table = ModernEmeraldEncounterData.TABLES.get(area);
            for (int slot = 0; slot < table.count(); slot++) {
                int internal = getInternalId(modernEncounters.get(area).get(slot).getSpecies());
                writeWord(table.speciesOffset(slot), internal);
            }
        }
        if (wildRandomized) {
            // SetUpMassOutbreakEncounter has already called CreateWildMon (with the proper native learnset).
            // B 0x0821CA92 skips only the four SetMonMoveSlot overrides; repel checks and creation remain intact.
            rom[OUTBREAK_MOVES_PATCH] = 0x0B;
            rom[OUTBREAK_MOVES_PATCH + 1] = (byte) 0xE0;
        }
        for (int slot = 0; slot < 3; slot++) {
            writeWord(ModernEmeraldStarterData.STARTERS + slot * 2, getInternalId(modernStarters.get(slot)));
        }
        if (startersChanged) ModernEmeraldStarterData.makeTypeExamplesGeneric(rom);
        saveTrades();
    }

    @Override public boolean saveRom(String filename, long seed, boolean directory) {
        if (directory) throw new UnsupportedOperationException("Modern Emerald is a GBA ROM file.");
        return super.saveRom(filename, seed, false);
    }

    @Override public boolean saveRomFile(String filename, long seed) {
        prepareSaveRom(); // This method is also public on AbstractGBRomHandler.
        return super.saveRomFile(filename, seed);
    }

    @Override public boolean isRomValid(PrintStream log) {
        boolean valid = originalRom != null && isSupportedImage(originalRom);
        if (log != null) log.println("Modern Emerald 3.6 verified layout: " + valid);
        return valid;
    }

    @Override public String getSupportLevel() { return "Partial (basic starters, wild Pokemon, no premature wild evolutions, NPC trades, move accuracy and PP; grey options are TODO)"; }
    @Override public List<Species> getSpecies() { return Collections.unmodifiableList(species); }
    @Override public List<Species> getSpeciesInclFormes() { return Collections.unmodifiableList(speciesWithFormes); }
    @Override public SpeciesSet getAltFormes() {
        return new SpeciesSet(speciesWithFormes.stream().filter(s -> s != null && !s.isBaseForme()).toList());
    }
    @Override public List<Move> getMoves() { return Collections.unmodifiableList(modernMoves); }
    @Override public List<Species> getStarters() {
        return List.copyOf(modernStarters);
    }
    @Override public boolean setStarters(List<Species> starters) {
        if (starters.size() != 3) throw new IllegalArgumentException("Exactly three starters are required");
        Set<Integer> choices = new HashSet<>();
        for (Species starter : starters) {
            int internal = getInternalId(starter);
            if (evolvedSpecies.contains(internal)
                    || !choices.add(ModernEmeraldSpeciesCatalog.selectionKey(internal))) {
                throw new IllegalArgumentException("Starters must be three different basic choices");
            }
        }
        modernStarters = List.copyOf(starters);
        startersChanged = true;
        return true;
    }
    @Override public List<Item> getStarterHeldItems() { return List.of(); }
    @Override public List<Item> getItems() { return List.of(); }
    @Override public List<EncounterArea> getEncounters(boolean time) { return ModernEmeraldEncounterData.copy(modernEncounters); }
    @Override public List<EncounterArea> getSortedEncounters(boolean time) { return getEncounters(time); }
    @Override public void setEncounters(boolean time, List<EncounterArea> areas) {
        if (areas.size() != modernEncounters.size()) throw new IllegalArgumentException("Encounter table count changed");
        for (int a = 0; a < areas.size(); a++) {
            var old = modernEncounters.get(a);
            var replacement = areas.get(a);
            if (old.size() != replacement.size() || old.getRate() != replacement.getRate()
                    || !Objects.equals(old.getDisplayName(), replacement.getDisplayName())) {
                throw new IllegalArgumentException("Encounter table layout changed");
            }
            for (int slot = 0; slot < old.size(); slot++) {
                if (old.get(slot).getLevel() != replacement.get(slot).getLevel()
                        || old.get(slot).getMaxLevel() != replacement.get(slot).getMaxLevel()) {
                    throw new IllegalArgumentException("Modern Emerald wild level changes remain unsupported");
                }
                getInternalId(replacement.get(slot).getSpecies());
            }
        }
        modernEncounters = ModernEmeraldEncounterData.copy(areas);
        wildRandomized = true;
    }
    @Override public boolean hasWildAltFormes() { return true; }
    @Override public List<Integer> getTMMoves() { return List.of(); }
    @Override public List<Integer> getMoveTutorMoves() { return List.of(); }
    @Override public List<StaticEncounter> getStaticPokemon() { return List.of(); }
    private void loadTrades() {
        modernTrades = new ArrayList<>();
        for (int i = 0; i < ModernEmeraldTradeData.COUNT; i++) {
            int offset = ModernEmeraldTradeData.offset(i);
            var trade = new InGameTrade(speciesByInternal.get(readWord(offset + 60)),
                    speciesByInternal.get(readWord(offset + 12)), true);
            trade.setNickname(readFixedLengthString(offset, 11));
            trade.setOtName(readFixedLengthString(offset + 47, 11));
            trade.setOtId((int) unsignedWord32(rom, offset + 28));
            trade.setHeldItem(new Item(readWord(offset + 44), "Original trade item"));
            trade.setIVs(java.util.stream.IntStream.range(20, 26).map(n -> rom[offset + n] & 255).toArray());
            if (getInternalId(trade.getGivenSpecies()) == 201) {
                trade.getGivenSpeciesHolder().setFormeNumber(ModernEmeraldTradeData.unownLetter(unsignedWord32(rom, offset + 40)));
            }
            modernTrades.add(trade);
        }
    }

    private static long unsignedWord32(byte[] image, int offset) {
        return Integer.toUnsignedLong((image[offset] & 255) | ((image[offset + 1] & 255) << 8)
                | ((image[offset + 2] & 255) << 16) | ((image[offset + 3] & 255) << 24));
    }

    private static InGameTrade copyTrade(InGameTrade old) {
        var copy = new InGameTrade(old.getRequestedSpecies(), old.getGivenSpecies(), true);
        copy.getGivenSpeciesHolder().setFormeNumber(old.getGivenSpeciesHolder().getFormeNumber());
        copy.setNickname(old.getNickname());
        copy.setOtName(old.getOtName());
        copy.setOtId(old.getOtId());
        copy.setHeldItem(old.getHeldItem());
        copy.setIVs(old.getIVs().clone());
        return copy;
    }

    @Override public List<InGameTrade> getInGameTrades() {
        return modernTrades.stream().map(ModernEmeraldRomHandler::copyTrade).toList();
    }

    @Override public void setInGameTrades(List<InGameTrade> trades) {
        if (trades.size() != ModernEmeraldTradeData.COUNT) throw new IllegalArgumentException("Exactly four NPC trades required");
        for (int i = 0; i < trades.size(); i++) {
            var trade = trades.get(i);
            var old = modernTrades.get(i);
            getInternalId(trade.getRequestedSpecies());
            getInternalId(trade.getGivenSpecies());
            if (trade.getIVs().length != 6 || Arrays.stream(trade.getIVs()).anyMatch(iv -> iv < 0 || iv > 31)
                    || !Objects.equals(old.getHeldItem(), trade.getHeldItem()) || old.getOtId() != trade.getOtId()
                    || !old.getOtName().equals(trade.getOtName())) throw new IllegalArgumentException("Unsupported trade field change");
            if (!trade.getNickname().equals(old.getNickname()) && !isTradeNicknameValid(trade.getNickname())) {
                throw new IllegalArgumentException("Nickname must contain 1-10 supported letters: " + trade.getNickname());
            }
        }
        modernTrades = trades.stream().map(ModernEmeraldRomHandler::copyTrade).toList();
        tradesChanged = true;
    }

    public boolean isTradeNicknameValid(String name) {
        return name != null && name.matches("[A-Za-z]{1,10}")
                && name.chars().allMatch(c -> d.containsKey(String.valueOf((char)c)));
    }

    private void saveTrades() {
        for (int i = 0; i < modernTrades.size(); i++) {
            var trade = modernTrades.get(i);
            int offset = ModernEmeraldTradeData.offset(i);
            writeWord(offset + 12, getInternalId(trade.getGivenSpecies()));
            writeWord(offset + 60, getInternalId(trade.getRequestedSpecies()));
            for (int stat = 0; stat < 6; stat++) rom[offset + 20 + stat] = (byte) trade.getIVs()[stat];
            // The original first trade forces slot 2, even when a new species has no second ability.
            // Keep that slot where it exists; otherwise select the native first ability.
            if (tradesChanged && rom[offset + 26] != 0 && trade.getGivenSpecies().getAbility2() == 0) {
                rom[offset + 26] = 0;
            }
            if (!trade.getNickname().equals(readFixedLengthString(offset, 11))) {
                writeModernText(offset, 11, trade.getNickname());
            }
            if (getInternalId(trade.getGivenSpecies()) == 201) {
                long original = unsignedWord32(rom, offset + 40);
                int letter = trade.getGivenSpeciesHolder().getFormeNumber();
                if (ModernEmeraldTradeData.unownLetter(original) != letter) {
                    long personality = ModernEmeraldTradeData.unownPersonality(original, unsignedWord32(rom, offset + 28), letter);
                    for (int b = 0; b < 4; b++) rom[offset + 40 + b] = (byte) (personality >>> (8 * b));
                }
            }
        }
        if (tradesChanged) {
            ModernEmeraldTradeData.patchRequestComparison(rom);
            for (var text : ModernEmeraldTradeData.FOLLOW_UP_TEXT) {
                writeModernText(text.offset(), text.length(), text.replacement());
            }
        }
    }

    private void writeModernText(int offset, int length, String text) {
        if (text.length() >= length) throw new IllegalArgumentException("Text does not fit");
        Arrays.fill(rom, offset, offset + length, (byte) 255);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            rom[offset + i] = c == '\n' ? (byte) 0xFE : Objects.requireNonNull(d.get(String.valueOf(c)));
        }
    }
    @Override public boolean hasCustomPlayerGraphicsSupport() { return false; }
    @Override public boolean hasPokemonPaletteSupport() { return false; }
    @Override public boolean hasMoveTutors() { return false; }
    @Override public boolean hasShopSupport() { return false; }
    @Override public boolean canChangeShopSizes() { return false; }
    @Override public boolean canSetIntroPokemon() { return false; }
    @Override public int miscTweaksAvailable() { return 0; }
    @Override public boolean hasPhysicalSpecialSplit() { return true; }
    @Override public TypeTable getTypeTable() {
        // Read the base chart for the shared UI's type list. Other in-game chart modes remain TODO.
        TypeTable table = new TypeTable(Type.getAllTypes(6));
        for (int offset = 0x6868A0; (rom[offset] & 255) != 0xFF; offset += 3) {
            int attacker = rom[offset] & 255;
            if (attacker == 0xFE) continue;
            Effectiveness effectiveness = switch (rom[offset + 2] & 255) {
                case 0 -> Effectiveness.ZERO;
                case 5 -> Effectiveness.HALF;
                case 20 -> Effectiveness.DOUBLE;
                default -> Effectiveness.NEUTRAL;
            };
            table.setEffectiveness(modernType(attacker), modernType(rom[offset + 1] & 255), effectiveness);
        }
        return table;
    }
}
