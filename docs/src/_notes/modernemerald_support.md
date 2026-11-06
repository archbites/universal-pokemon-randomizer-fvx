# Modern Emerald foundation

The `codex/modernemerald` branch starts from `master` at `a3cf6f5a`. Polished Crystal is a separate Gen 2 implementation and is not a dependency. The previous uncommitted Polished Crystal work is preserved in the Git stash named `Polished Crystal work preserved before Modern Emerald foundation`.

## Supported build and identification

This foundation targets the supplied **Pokémon Modern Emerald 3.6**, with its matching `PokemonModernEmerald3.6.sym`. It does not claim support for newer builds compiled from the current source checkout.

- ROM size: 33,554,432 bytes.
- Original CRC32: `263A1187`.
- Original SHA-256: `bc7286dca750cf735480fece3ff68c7d86ecbf8391031b5ca2964da78fc945a1`.
- Recognition fingerprint: SHA-256 with audited move accuracy/PP bytes (excluding Struggle), wild species words, the outbreak move fix, starter words/text, and the NPC trade fields/text/request bridge masked: `096c81b4884864f64a56a01e8cadeead64b86fb8ba4c4e9d6ccd1a1a1ea514c0`. The request bridge must be either completely absent or exactly the approved code and pointer; arbitrary executable changes are rejected.

The ROM retains Emerald's `BPEE` header. It is opened by its own `ModernEmeraldRomHandler` before the retail Gen 3 handler. The expanded GF-header dex count prevents unrecognized expanded Emerald builds from falling back to vanilla Emerald offsets. Unknown layouts are rejected. Our own randomized outputs remain recognizable without a sidecar symbol file.

No ROM assets or symbol files are included in this repository.

## Capability checklist

Checked entries are enabled. Everything else stays visible but greyed out; grey means pending implementation **or verification**, not necessarily impossible. Tabs remain browsable. This boundary also applies to imported settings, saved settings, CLI use, and direct `GameRandomizer` callers.

| Status | Option group | Evidence / remaining work |
| --- | --- | --- |
| Verified | Move PP | `gBattleMoves` + `id * 12 + 4`; shared PP initialization/restoration reads this field in `src/pokemon.c` and battle controllers. Includes added moves. Struggle is excluded. |
| Verified | Move accuracy | `gBattleMoves` + `id * 12 + 3`; the battle accuracy commands read this field. The existing algorithm preserves accuracy-zero moves and excludes Struggle. Normal move effects and ability/weather accuracy rules still apply. |
| Verified | Wild Pokémon; maximum possible replacements; no type or evolution restrictions; optional Catch Em All | All audited normal encounter tables, Feebas tiles, and future TV outbreaks. Includes all real added species/forms and legendaries with the weighting below. Catch Em All defaults on when enabling wild randomization. |
| Verified | Starters: Random (Any Basic Pokémon), type restriction None | Three distinct basic choices, classified by incoming evolutions in the actual 3.6 binary. Form weighting is automatic. No other starter option is enabled. |
| Verified | No premature evolutions | Wild replacements obey fixed or estimated evolution thresholds and their complete incoming paths. Catch Em All retains every basic choice on Hoenn routes and places higher evolved forms in suitable later areas. NPC trades are explicitly exempt. |
| Verified | In-game trades: randomize both requested and given Pokémon; optional nicknames and IVs | Four ordinary NPC trades. Uses the same 431 selection groups. Requests accept all forms with the same displayed name. Native perfect-IV overrides and trade evolutions remain active. |
| TODO | Other replacement zones, type/evolution restrictions, strength matching, wild levels, catch rates and held items | These controls remain grey. Imported presets are normalized to maximum replacements and no type/evolution restrictions. |
| TODO | BST, base stats, EXP curves, types, abilities, catch rates, wild held items | The 3.6 species record is **40 bytes**, versus vanilla's 28. Modern Emerald also has old/current/new trait fields and in-game mode overrides. The vanilla species writer is unsafe. All real species and stat-bearing forms are loaded read-only; trait writing remains disabled. |
| TODO | Generation restrictions and manual form filters | Full form identities and evolution-level filtering are implemented. Other optional eligibility filters remain unverified. Local dex numbers are not National Dex IDs. |
| TODO | Evolution changes and evolution conveniences | The eight-slot evolution graph is decoded read-only for basic classification and minimum appearance levels. Rewriting methods, targets or levels remains unsupported; runtime evolution challenges are untouched. |
| TODO | Other starter modes/options, rival continuity, static/gift/roaming Pokémon, other trade options | Custom choices, other starter modes, type restrictions, held items, BST limits, rival continuity, given-only trades, trade OTs and trade items stay grey. Wonder Trade and self-trade machines retain native behavior. |
| TODO | Move power, type, category, names, generation updates | Data and runtime move-mode behavior differ from retail. Category is a per-move field. These options have not been independently ported and validated. Move names may be straightforward to add later. |
| TODO | Level-up/egg moves, starting moves, Metronome mode | Both modern and original learnset tables exist. Updating only the vanilla table would miss an in-game mode. Free-space relocation and added moves need validation. |
| TODO | Trainers, names/classes, levels, held items, parties, movesets, battle styles | Modified trainer data, difficulty/rematch behavior, runtime overrides, and expanded IDs need an audited adapter. Vanilla unconditional trainer saves are bypassed. |
| TODO | TMs/HMs, tutors, compatibility, sanity options | Separate old/modern TM compatibility and learnsets exist. Move text, tutor scripts, and reusable-TM behavior need verification for each mode. |
| TODO | Field items, shops, prices, Pickup, starter/encounter held items | Relocated scripts, expanded item tables (`gItems` and `gItems2`), new items, and challenge prices require an item-ID adapter and script audit. |
| TODO | Type effectiveness / updates | Base and Gen VI charts coexist with optional custom rules and Fairy. Vanilla fixed-size chart writes do not represent all modes. |
| TODO | Pokémon palettes, shiny palettes, player graphics | Multiple modern/original shiny tables, added forms/followers, and selectable outfits require a broader graphics adapter. |
| TODO | Intro Pokémon, code tweaks, race mode | Vanilla executable offsets, signatures, text writes, and intro/check-value behavior are not verified for Modern Emerald. All code tweaks remain disabled. |

The save path starts from the original bytes and writes only whitelisted move fields, wild species words, basic starter words/text, NPC trade data/text/request bridge, plus the outbreak move fix when wild randomization is applied. It never calls vanilla map preprocessing, free-space reclamation, species/evolution saves, trainer saves, or palette saves. Unsupported imported settings are discarded by a whitelist; adding a future option requires explicitly extending that whitelist and its tests.

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

With No premature evolutions **off**, Catch Em All uses ordinary Routes 101–134 as coverage anchors. There are 676 usable slots in each native view: original, modern day, and modern night. Shared day/night slots are assigned first; each view then receives its missing choices without overwriting shared placements. Every one of the 431 choices is guaranteed in each view across a full playthrough, including fishing/Surf access. Postgame-switching mode is covered on both sides of its original-to-modern transition. Remaining locations are randomized independently. This unrestricted guarantee does not rely on a TV event, a postgame-only special map, or an uncatchable battle facility. With No premature evolutions **on**, the coverage strategy below allows postgame areas for evolved forms.

The compiled 3.6 `GetCurrentMapWildMonHeaderId` was disassembled to verify original/day/night selection and the night-to-day fallback. A test independently follows those binary headers rather than trusting only the manifest's coverage tags.

TV outbreaks normally overwrite a generated Pokémon's moves with fixed Seedot/Nuzleaf/Skitty moves. When wild randomization is applied, the two-byte instruction at `0x21CA78` changes from `0B 4E` (`ldr r6`) to `0B E0` (`b 0x0821CA92`). This skips only the four move overrides, after native `CreateWildMon` has already generated the replacement's correct level-up moves for the selected original/modern moveset mode. Repel checks, level, location, creation, and the success epilogue are retained. Both original and patched instructions are explicitly validated when recognizing a ROM. Existing save-file TV shows/outbreak payloads cannot be rewritten by a ROM-only tool; newly generated shows use the randomized outbreak species.

Modern Emerald 3.6 already repairs Mew/Deoxys's fateful-encounter status in `IsBattlerModernFatefulEncounter` (`0x0AD76C`); binary disassembly confirmed that behavior. Vanilla obedience patches are not applied.

The game's own settings remain authoritative. Leave **Modern Emerald's built-in wild randomizer OFF** to use this application's encounters and Catch Em All guarantee. Enabling it intentionally remaps the already randomized species, so the application's coverage guarantee no longer applies. Native challenges, difficulty scaling, weather, and abilities retain their normal effects. Battle Pike/Pyramid encounters are uncatchable and left alone; scripted gifts, static encounters and roamers retain their separate disabled controls.

## Basic starters

The user explicitly chose **actual incoming evolution entries**, rather than canonical evolution families or the game's separate randomizer tier lists, as the definition of "basic." Reading all eight records for every real species finds 217 distinct evolution targets. This leaves **214 basic choices containing 217 physical species/form records**. Wyrdeer, Ursaluna, Bloodmoon Ursaluna and Kleavor therefore qualify because nothing evolves into them in the 3.6 evolution table. Baby Pokémon exclude their evolved forms correctly. Single-stage Pokémon, legendaries, regional birds and all four grouped Deoxys forms remain eligible.

The user also explicitly kept *every* qualifying basic eligible, including Abra, Magikarp and Wynaut. Their native level-5 moves are retained, even when they have no attacking move. No hidden damage-move, strength, type, legendary, generation or canonical-family filter is imposed. Pick three distinct groups without replacement, then pick the form within each group.

Only the three consecutive species words at `sStarterMon=0x92EB54` are changed. Binary disassembly confirmed that `GetStarterPokemon` (`0x1EB8AC`) reads this shared table and is used by the starter label, preview, cry, `CB2_GiveStarter`, party checks and credits. The player still receives the chosen Pokémon at level 5 through the native `ScriptGiveMon` path. Shiny preview flags already work by slot rather than by hardcoded species, so their native handling is preserved. Cosmetic PID-dependent appearances such as Unown letters and Spinda spots are still determined when the Pokémon is created.

One Petalburg NPC uses hardcoded type-example text. When starters change, remove "your " from the three examples at `0x26491C`, `0x2649A1` and `0x264A23`. The NPC can still correctly explain "For example, Treecko is a Grass type" without claiming that Treecko is the randomized starter. No pointers move; text remains in its original bounds. The examples are untouched for an unchanged starter run.

Rival teams remain the game's scripted teams because rival continuity is a separate unsupported option. Keep the game's **built-in starter randomizer and one-type challenge OFF**, since either overrides `GetStarterPokemon` with the game's own choices. No native moves, evolutions or trainer records are rewritten by this feature.

The Modern Emerald dropdown models now include all stat-bearing forms, so a reloaded Deoxys-Attack, Bloodmoon Ursaluna or Galarian starter appears by name rather than leaving a missing selection. Custom selection stays disabled. All 434 loaded names and all three Swing dropdown models are checked for readable Latin names; the reported garbled text was not reproduced in the current build, so use the rebuilt `random/build/libs/UPR-FVX.jar` when checking it.

## No premature evolutions

The global checkbox is supported for **wild replacements**. It does not rewrite the game's evolution mechanics or change encounter levels. Basic starters already have no incoming evolution path. **NPC trades are exempt by user choice**, so a level-1 evolved gift and its native trade evolution are still allowed. Trade request matching and Deoxys/Unown form rolls are unchanged.

`ModernEmeraldEvolutionData` decodes all **217 actual evolution entries**, including the hack's eight slots, gender/time level requirements, held-item level-ups, move requirements, and Shedinja's separate creation entry. Fixed level requirements are exact. Non-level methods use the existing randomizer's BST-based estimator against this ROM's level-evolution samples, retaining its stage-spacing adjustment. The full incoming path is then checked so a later stage cannot appear below its predecessor's threshold; methods requiring a level-up also need one further level if the earlier stage was only just obtainable. Where multiple paths exist, the cheapest valid path suffices.

Examples of minimum wild appearance levels: Ivysaur 16, Venusaur 32, Shedinja 20, Raichu 32, Gengar/Alakazam 38, Scizor 46, Rhyperior 53, Dragonite/Tyranitar 55. Estimates for stones, trades, friendship, beauty, and move requirements are balance thresholds, not claims that those methods intrinsically require those levels. Known move-based thresholds are also audited against both native learnset tables; the estimates do not undercut the earliest recorded level for the required move where it is present. Native move reminders/tutors and mode-specific learning behavior are retained.

All **217 physical records with no incoming ROM path** remain eligible at level 1, forming **214 basic choice groups**. This includes Wyrdeer, both Ursaluna forms and Kleavor, even though their canonical pre-evolutions exist elsewhere in the franchise. No canonical links are invented. Deoxys's forms share one eligible choice and receive the second form roll; regional birds and Bloodmoon retain their separate choice weights.

Each encounter uses its **minimum** level for eligibility, so an evolved Pokémon cannot appear illegally at the bottom of a variable level range. Of the eligible choice groups, one is selected first, then an eligible form. This covers normal tables, the level-20–25 Feebas-tile slot, and future TV outbreaks. Levels, rates, slot probabilities, evolution entries, moves and native mode-selection code remain unchanged.

With Catch Em All enabled, a first pass guarantees every basic choice on Routes 101–134 in each original/day/night view, using the lowest available slots. A second pass reserves suitable levels for the remaining groups across ordinary catchable maps, including caves and late-game/postgame locations. Harder-to-place groups are assigned first. Native headers supply mode coverage, including night-to-day fallbacks and aliased physical tables. Single-header special maps and Altering Cave's nine independent sets are excluded from coverage promises. No guarantee depends on TV outbreaks or battle facilities. All 431 groups remain covered per view; the five groups with thresholds above 50 can use the higher-level cave/island tables instead of the routes' level-50 ceiling. Basic family members remain obtainable on Hoenn routes, so evolving them yourself remains an alternative.

The game's own settings take priority. Built-in random wild species, randomized evolution methods/targets, or runtime level overrides can change the baseline rules after ROM generation; their native behavior is preserved, and the static eligibility/coverage guarantees apply to the unmodified encounter/evolution modes. Turning this checkbox off reproduces the previous wild algorithm exactly.

Kadabra, Haunter, Machoke and Graveler still use **trade evolution** in the supplied ROM. Modern Emerald's **Self-Trader on Lilycove Department Store's ground floor** lets you trigger those evolutions without another game. Held-item trade evolutions still require the appropriate item. The machine and all evolution routines are untouched by this feature.

## NPC trades and nicknames

Only **Randomize both requested & given Pokémon**, **Randomize nicknames**, and **Randomize IVs** are enabled. The four ordinary trades are in Rustboro, Fortree, Pacifidlog and the Battle Frontier lounge. Each side samples the established 431 choice groups, then a physical form. Given groups and requested groups do not repeat within their own four-entry lists. A trade cannot request a displayed species name identical to its gift's name. No availability or early-game accessibility guarantee is added for requests.

The table `sIngameTrades` is at `0xBC5FF8`, with four **64-byte** records, rather than the retail handler's 60-byte layout. Writable fields per record are the 11-byte nickname at `+0`, given species at `+12`, six normal IVs at `+20`, ability slot at `+26` only to repair an invalid forced second slot, personality at `+40` only for Unown's rolled letter, and requested species at `+60`. Normal IVs are independent uniform values from 0 through 31. The separate perfect-IV bank at `+14..19` remains six 31s. Original OT names/IDs/gender, held items/mail, contest conditions, sheen, and level-matching behavior are retained. The native creation routine and game settings still determine moves, stats and mode effects. The first trade's forced second ability falls back to slot one if the new Pokémon has no second ability; this is a compatibility repair, not ability randomization.

Native trade evolutions remain active by user choice: a received Kadabra can evolve into Alakazam. The trade machine in Lilycove (including temporary Mew/storage branches) and Oldale Wonder Trade are separate systems and are unchanged. Existing save-file completion flags still apply; this tool does not reset completed trades.

**Request matching follows the name the NPC displays.** All Deoxys forms satisfy a Deoxys request; either Ursaluna satisfies an Ursaluna request; either regional version satisfies each bird's request. Any Unown letter already shares internal species ID 201. This acceptance policy is separate from selection weighting: Bloodmoon and each Galarian bird still receive independent chances, and Deoxys still gets one chance followed by a uniform form roll. The log identifies the rolled physical form even where another form can satisfy that request.

Only the `GetTradeSpecies` special-table pointer at `0x236AD8` is redirected, from `0x081F6089` to a 68-byte Thumb-1 bridge at verified unused offset `0x1F00000`. The bridge calls the original egg-rejecting party-species routine and native `StringCompare`; matching displayed names return the requested ID so all four unchanged NPC scripts accept them. No link-trade, Wonder Trade or self-trade function is redirected. Assembly source is `romio/src/main/resources/com/uprfvx/romio/patches/modernemerald_trade_request.s`. Three hardcoded post-trade lines are made generic, in their original bounded text allocations (`0x27425D/96`, `0x2608DF/114`, `0x2C6BBD/70`), without moving pointers.

Unown gifts roll one of 28 letters only after their species is picked. The personality writer retains the original nature and shiny status while encoding that letter; Unown is genderless. Other personalities remain unchanged. Deoxys/regionals/Bloodmoon are stored using actual species IDs; Castform, Arceus, Spinda and other runtime appearances retain native behavior.

`PokemonNicknames.txt` replaces all 20 old defaults with **1,000 distinct names**, all 1–10 ASCII letters, drawn from astronomy, geography, minerals, weather, music, plants, science and evocative nouns. Examples: Obsidian, Denali, Requiem, Monsoon, Atacama, Nebula, Solstice. No gender is inferred from the received Pokémon. Four different nicknames are selected per randomization. External custom-name files still take precedence; standalone JARs fall back to their bundled defaults when an external file is absent. This default nickname pool applies to other supported ROMs too.

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

The basic-starter suite adds five integration tests: exact graph-based eligibility, 600 seeds of distinct basic choices with unsupported options stripped, starter-only byte boundaries and unchanged-save round trips, functional-form persistence, and combined starter/wild coverage with unchanged moves/evolutions. Swing tests also exercise only the requested starter controls, readable complete dropdown models, selected functional forms, preset normalization and retail restoration. The full run has 180 romio tests and 15 random tests; all fifteen Modern Emerald tests pass, with the same independent RSE image-comparison failure in the broader suite. Emulator playtesting is still outstanding.

The trade-support run executes 180 romio tests and 21 random tests. All 21 random tests and all Modern Emerald tests pass; the same independent RSE graphics fixture comparison is the sole broader-suite failure. New checks cover 4,000 randomized trade sets, normal/perfect IV separation, optional nickname/IV preservation, all physical form identities, all 28 Unown personality encodings with unchanged nature/shiny status, exact trade-only byte boundaries, combined wild/starter/trade saves, logs, output reload, no-op round trips, GUI/preset restrictions, and the 1,000-name pool.

`tools/verify_modernemerald_trades.py` independently assembles and executes the bridge with Unicorn against the supplied ROM. Its **1,322 ARM execution cases** cover every one of the 434 real species/form IDs, all shared-name matches, mismatches, eggs, return flow and register/stack preservation. Eight further executions of the original trade-creation routine verify randomized normal IVs versus perfect-IV override across all four NPC trades, plus the native species/level arguments. It requires `keystone-engine==0.9.2` and `unicorn==2.1.4` only for development verification. A full emulator playthrough remains outstanding.

The actual standalone JAR CLI was also run with seed `20261106` and `build/modernemerald-test/modernemerald-starters-wild-trades.rnqs`, producing `modernemerald-starters-wild-trades-test.gba` and its log. These ignored local artifacts combine the requested wild settings, random basic starters, both sides of NPC trades, nicknames and IVs. Run the current build with `java -jar random/build/libs/UPR-FVX.jar`.

The final evolution-filter suite has **180 romio tests and 26 random tests**. All 26 random tests and all Modern Emerald tests pass; the same existing RSE image-fixture comparison remains the sole broader-suite failure. Five new tests cover all native evolution methods and no-path roots, exact/estimated thresholds and chain legality, 40 seeds of legal per-view Catch Em All coverage with all 214 basic groups on routes, unconstrained replacement mode, variable level ranges, saved-ROM byte boundaries, independent native-header traversal, unchanged saves, deterministic seeds, preset serialization, and the explicit NPC-trade exemption. GUI tests verify the checkbox and imported/exported settings.

The rebuilt JAR was exercised with `build/modernemerald-test/modernemerald-final.rnqs` and seed `20261106`, producing `modernemerald-final-test.gba` plus its log and the readable `modernemerald-evolution-levels.tsv` audit. This preset includes every requested option and No premature evolutions. The previous preset with the checkbox off still produces a byte-identical ROM to the pre-feature build for the same seed. Full emulator gameplay testing remains outstanding.
