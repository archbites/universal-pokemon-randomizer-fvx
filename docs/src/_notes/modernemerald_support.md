# Modern Emerald foundation

The `codex/modernemerald` branch starts from `master` at `a3cf6f5a`. Polished Crystal is a separate Gen 2 implementation and is not a dependency. The previous uncommitted Polished Crystal work is preserved in the Git stash named `Polished Crystal work preserved before Modern Emerald foundation`.

## Supported build and identification

This foundation targets the supplied **Pokémon Modern Emerald 3.6**, with its matching `PokemonModernEmerald3.6.sym`. It does not claim support for newer builds compiled from the current source checkout.

- ROM size: 33,554,432 bytes.
- Original CRC32: `263A1187`.
- Original SHA-256: `bc7286dca750cf735480fece3ff68c7d86ecbf8391031b5ca2964da78fc945a1`.
- Recognition fingerprint: SHA-256 with the audited move accuracy/PP bytes (excluding Struggle), wild species words, and two-byte outbreak move fix masked: `ccacc6c40623236578ea76689ce6daa3e9389f8fb4762b28184e547e4a2cb671`.

The ROM retains Emerald's `BPEE` header. It is opened by its own `ModernEmeraldRomHandler` before the retail Gen 3 handler. The expanded GF-header dex count prevents unrecognized expanded Emerald builds from falling back to vanilla Emerald offsets. Unknown layouts are rejected. Our own randomized outputs remain recognizable without a sidecar symbol file.

No ROM assets or symbol files are included in this repository.

## Capability checklist

Checked entries are enabled. Everything else stays visible but greyed out; grey means pending implementation **or verification**, not necessarily impossible. Tabs remain browsable. This boundary also applies to imported settings, saved settings, CLI use, and direct `GameRandomizer` callers.

| Status | Option group | Evidence / remaining work |
| --- | --- | --- |
| Verified | Move PP | `gBattleMoves` + `id * 12 + 4`; shared PP initialization/restoration reads this field in `src/pokemon.c` and battle controllers. Includes added moves. Struggle is excluded. |
| Verified | Move accuracy | `gBattleMoves` + `id * 12 + 3`; the battle accuracy commands read this field. The existing algorithm preserves accuracy-zero moves and excludes Struggle. Normal move effects and ability/weather accuracy rules still apply. |
| Verified | Wild Pokémon; maximum possible replacements; no type or evolution restrictions; optional Catch Em All | All audited normal encounter tables, Feebas tiles, and future TV outbreaks. Includes all real added species/forms and legendaries with the weighting below. Catch Em All defaults on when enabling wild randomization. |
| TODO | Other replacement zones, type/evolution restrictions, strength matching, wild levels, catch rates and held items | These controls remain grey. Imported presets are normalized to maximum replacements and no type/evolution restrictions. |
| TODO | BST, base stats, EXP curves, types, abilities, catch rates, wild held items | The 3.6 species record is **40 bytes**, versus vanilla's 28. Modern Emerald also has old/current/new trait fields and in-game mode overrides. The vanilla species writer is unsafe. All real species and stat-bearing forms are loaded read-only; trait writing remains disabled. |
| TODO | Generation restrictions, manual form filters, premature evolution filters | Full form identities are loaded and wild weighting is automatic. Optional eligibility filters and the evolution graph still need their own implementation and tests. Local dex numbers are not National Dex IDs. |
| TODO | Evolutions and evolution conveniences | Modern source declares eight evolution slots per species instead of vanilla's five, plus new methods and runtime challenges. Requires an explicit codec and continuity tests. |
| TODO | Starters, rival continuity, static/gift/roaming Pokémon, trades | Expanded IDs, relocated scripts, special encounters, and built-in randomizer behavior need dedicated writers. Starter dropdowns only preview the unchanged Treecko/Torchic/Mudkip choices. |
| TODO | Move power, type, category, names, generation updates | Data and runtime move-mode behavior differ from retail. Category is a per-move field. These options have not been independently ported and validated. Move names may be straightforward to add later. |
| TODO | Level-up/egg moves, starting moves, Metronome mode | Both modern and original learnset tables exist. Updating only the vanilla table would miss an in-game mode. Free-space relocation and added moves need validation. |
| TODO | Trainers, names/classes, levels, held items, parties, movesets, battle styles | Modified trainer data, difficulty/rematch behavior, runtime overrides, and expanded IDs need an audited adapter. Vanilla unconditional trainer saves are bypassed. |
| TODO | TMs/HMs, tutors, compatibility, sanity options | Separate old/modern TM compatibility and learnsets exist. Move text, tutor scripts, and reusable-TM behavior need verification for each mode. |
| TODO | Field items, shops, prices, Pickup, starter/encounter held items | Relocated scripts, expanded item tables (`gItems` and `gItems2`), new items, and challenge prices require an item-ID adapter and script audit. |
| TODO | Type effectiveness / updates | Base and Gen VI charts coexist with optional custom rules and Fairy. Vanilla fixed-size chart writes do not represent all modes. |
| TODO | Pokémon palettes, shiny palettes, player graphics | Multiple modern/original shiny tables, added forms/followers, and selectable outfits require a broader graphics adapter. |
| TODO | Intro Pokémon, code tweaks, race mode | Vanilla executable offsets, signatures, text writes, and intro/check-value behavior are not verified for Modern Emerald. All code tweaks remain disabled. |

The save path starts from the original bytes and writes only whitelisted move fields and wild species words, plus the small outbreak move fix when wild randomization is applied. It never calls vanilla map preprocessing, free-space reclamation, species/evolution saves, trainer saves, or palette saves. Unsupported imported settings are discarded by a whitelist; adding a future option requires explicitly extending that whitelist and its tests.

## Species and form policy

The catalog loads **427 base species plus seven stat-bearing forms = 434 physical records**, represented by **431 equally weighted choices**:

| Identity | Selection policy |
| --- | --- |
| Unown | One chance. All 28 letters are registered; native random personality selects the appearance after the encounter species is selected. Never encode a letter using the old-Unown or debug species IDs. |
| Deoxys | One chance, then an equal roll among Normal/Attack/Defense/Speed. These are functional forms grouped by the user's explicit choice, rather than mislabeled as cosmetic data. |
| Articuno, Zapdos, Moltres and their Galarian forms | Six independent chances. Each regional bird must be covered separately. |
| Ursaluna and Bloodmoon Ursaluna | Two independent chances. Bloodmoon is a form of the same National Dex species, but is treated as sufficiently different for weighting. The 3.6 binary has no normal Ursaring evolution entry; the source uses special Wonder Trade substitutions. Catch Em All places both forms independently. |
| Spinda | One chance; native personality supplies spots. |
| Castform | One chance; native weather/Forecast supplies battle transformations. |
| Arceus | One chance; native held-item/Multitype behavior supplies type changes. |
| Shiny/modern palette variants | Native shiny/palette selection, with no extra species weight. |

The excluded physical slots are NONE, Egg, old-Unown placeholders 252–276, unused slot 457, debug slot 458 (misleadingly named "Unown"), and graphics-only Unown IDs. All 434 eligible records participate; functional forms use explicit internal-ID mappings rather than assuming a dex number is a ROM species ID.

Catch Em All cycles through *choices*, so it guarantees one Deoxys group rather than every Deoxys form. A form roll follows the group pick. Form counts never multiply a group's selection weight.

## Encounter coverage and special cases

`modernemerald_36_encounters.tsv` describes **495 physical tables and 4,609 catchable encounter slots**, deduplicated by ROM storage: 493 active normal tables, the hardcoded Route 119 Feebas slot, and the five TV outbreak entries. The zero-rate unused table is untouched. Level ranges, encounter rates, table pointers, slot probabilities, outbreak locations, and original move tables remain unchanged.

For Catch Em All, the placement pass uses ordinary Routes 101–134 as coverage anchors. There are 676 usable slots in each native view: original, modern day, and modern night. Shared day/night slots are assigned first; each view then receives its missing choices without overwriting shared placements. Every one of the 431 choices is guaranteed in each view across a full playthrough, including fishing/Surf access. Postgame-switching mode is covered on both sides of its original-to-modern transition. Remaining locations are randomized independently. No guarantee relies on a TV event, a postgame-only special map, or an uncatchable battle facility.

The compiled 3.6 `GetCurrentMapWildMonHeaderId` was disassembled to verify original/day/night selection and the night-to-day fallback. A test independently follows those binary headers rather than trusting only the manifest's coverage tags.

TV outbreaks normally overwrite a generated Pokémon's moves with fixed Seedot/Nuzleaf/Skitty moves. When wild randomization is applied, the two-byte instruction at `0x21CA78` changes from `0B 4E` (`ldr r6`) to `0B E0` (`b 0x0821CA92`). This skips only the four move overrides, after native `CreateWildMon` has already generated the replacement's correct level-up moves for the selected original/modern moveset mode. Repel checks, level, location, creation, and the success epilogue are retained. Both original and patched instructions are explicitly validated when recognizing a ROM. Existing save-file TV shows/outbreak payloads cannot be rewritten by a ROM-only tool; newly generated shows use the randomized outbreak species.

Modern Emerald 3.6 already repairs Mew/Deoxys's fateful-encounter status in `IsBattlerModernFatefulEncounter` (`0x0AD76C`); binary disassembly confirmed that behavior. Vanilla obedience patches are not applied.

Keep **Modern Emerald's built-in wild randomizer OFF**. It otherwise remaps the already randomized species and invalidates the coverage guarantee. Native challenges, difficulty scaling, weather, and abilities retain their normal effects. Battle Pike/Pyramid encounters are uncatchable and left alone; scripted gifts, static encounters and roamers retain their separate disabled controls.

## Source references and validation

Compared the local `pokeemerald` and `pokemonmodernemerald` source trees, especially `include/pokemon.h`, `include/constants/pokemon.h`, `src/rom_header_gf.c`, `src/pokemon.c`, `src/battle_script_commands.c`, `src/wild_encounter.c`, and their data tables. The Modern Emerald checkout was `e983cc52f7d9cde0a54ff2fc430f653c26086c8c` (newer than the supplied 3.6 binary); binary sizes and offsets come from the matching 3.6 symbols, not from assuming this checkout builds identically. Upstream: <https://github.com/resetes12/pokeemerald>.

Important 3.6 symbols (file offsets): `gBattleMoves=0x8E0898`, `gMoveNames=0x6E88F4`, `gSpeciesInfo=0x8E5960`, `gSpeciesNames=0x6E7518`, `gTypeEffectiveness=0x6868A0`.

Set `MODERN_EMERALD_ROM` to your local 3.6 `.gba` to run integration tests. ROM-dependent tests skip when it is unset. Example from PowerShell:

```powershell
$env:MODERN_EMERALD_ROM = 'C:\path\to\Modern Emerald 3.6.gba'
.\gradlew.bat :romio:test :random:test --offline --rerun-tasks
```

Tests verify release detection through `RomOpener`, unknown-build rejection, unchanged save equality, all four combinations of PP/accuracy, exact changed-byte boundaries across the entire ROM, successful logs, output reload, preset filtering, only verified UI controls remaining enabled, and restoration of the available retail controls after repeated ROM switches. Wild-specific tests verify per-view coverage across 40 seeds, all 434 real records appearing across those runs, separate Bloodmoon/regional coverage, independent maximum replacements, unchanged levels/rates, the outbreak fix, native-header coverage in a saved ROM, same-seed reproducibility, and rejection of debug species in otherwise valid layouts. These are source and binary integration checks; an emulator playthrough has not been performed.

The initial foundation's broader suite run had one independent existing graphics comparison failure: `PlayerCharacterGraphicsTest.fromSheetGiveSameImagesAndPalsAsFromSeparate_RSE` (separate images versus sprite sheet). That test reads player-image fixtures and does not exercise the ROM handlers or the new UI path; its implementation and graphics code were not changed here. Gradle currently ignores test failures, so inspect the XML results rather than relying only on its exit status.

The final wild-support suite run executed 180 romio tests and 10 random tests. All ten Modern Emerald tests passed; the same independent RSE graphics comparison remains the only suite failure. The built JAR was also exercised through its actual CLI with `build/modernemerald-test/modernemerald-wild.rnqs` and seed `20261106`. The resulting `modernemerald-wild-test.gba` was audited independently: 431 choices are present in each native route view, the move table is byte-identical to the source ROM, and all 6,832 changed bytes are inside the audited wild species words or outbreak instruction. Those local test artifacts are under the ignored build directory.
