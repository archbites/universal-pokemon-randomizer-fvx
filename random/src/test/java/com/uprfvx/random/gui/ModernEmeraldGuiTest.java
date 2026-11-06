package com.uprfvx.random.gui;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.*;
import com.uprfvx.romio.romhandlers.romentries.Gen3RomEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModernEmeraldGuiTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "MODERN_EMERALD_ROM", matches = ".+")
    void onlyVerifiedOptionsRemainEnabledAndRetailStateIsRestored() throws Exception {
        var modern = new ModernEmeraldRomHandler();
        assertTrue(modern.loadRom(System.getenv("MODERN_EMERALD_ROM")));
        SwingUtilities.invokeAndWait(() -> {
            try {
                Field frame = RandomizerGUI.class.getDeclaredField("frame");
                frame.setAccessible(true);
                frame.set(null, new JFrame());
            } catch (Exception e) { throw new RuntimeException(e); }
            RandomizerGUI gui = new RandomizerGUI(false);
            try {
                var retail = new RetailGuiFixture(modern);
                load(gui, retail);
                Map<String, String> baseline = snapshot(gui);
                for (int repeat = 0; repeat < 2; repeat++) {
                    load(gui, modern);
                    assertSame(modern, field(gui, "romHandler"));
                    assertTrue(((JLabel) field(gui, "romNameLabel")).getText().contains("Modern Emerald 3.6"));
                    assertTrue(((JLabel) field(gui, "romSupportLabel")).getText().contains("Partial"));
                    JTabbedPane tabs = (JTabbedPane) field(gui, "tabbedPane1");
                    for (int i = 0; i < tabs.getTabCount(); i++) assertTrue(tabs.isEnabledAt(i));
                    for (Field f : RandomizerGUI.class.getDeclaredFields()) {
                        f.setAccessible(true);
                        Object value = f.get(gui);
                        if (!(value instanceof AbstractButton || value instanceof JComboBox<?>
                                || value instanceof JSlider || value instanceof JSpinner)) continue;
                        Component component = (Component) value;
                        if (!SwingUtilities.isDescendingFrom(component, tabs)) continue;
                        boolean allowed = f.getName().equals("mdRandomizeMoveAccuracyCheckBox")
                                || f.getName().equals("mdRandomizeMovePPCheckBox")
                                || f.getName().equals("wpRandomizeWildPokemonCheckBox")
                                || f.getName().equals("spUnchangedRadioButton")
                                || f.getName().equals("spRandomBasicRadioButton")
                                || f.getName().equals("igtUnchangedRadioButton")
                                || f.getName().equals("igtRandomizeBothRequestedGivenRadioButton");
                        assertEquals(allowed, component.isEnabled(), f.getName());
                    }
                    for (String name : List.of("noRandomIntroMonCheckBox", "mdRandomizeMovePowerCheckBox",
                            "spRandomCompletelyRadioButton",
                            "ppalRandomRadioButton", "cpgCustomRadioButton", "shRandomRadioButton")) {
                        JComponent control = (JComponent) field(gui, name);
                        assertTrue(control.isVisible(), name);
                        assertFalse(control.isEnabled(), name);
                        assertTrue(control.getToolTipText().contains("TODO"), name);
                    }
                    JCheckBox wild = (JCheckBox) field(gui, "wpRandomizeWildPokemonCheckBox");
                    wild.doClick();
                    assertTrue(((JRadioButton) field(gui, "wpZoneNoneRadioButton")).isSelected());
                    assertTrue(((JCheckBox) field(gui, "wpCatchEmAllModeCheckBox")).isSelected());
                    assertFalse(((JCheckBox) field(gui, "wpDontUseLegendariesCheckBox")).isSelected());
                    assertTrue(((JCheckBox) field(gui, "wpCatchEmAllModeCheckBox")).isEnabled());
                    wild.doClick();
                    assertFalse(((JCheckBox) field(gui, "wpCatchEmAllModeCheckBox")).isEnabled());
                    for (String name : List.of("spComboBox1", "spComboBox2", "spComboBox3")) {
                        JComboBox<?> dropdown = (JComboBox<?>) field(gui, name);
                        assertEquals(435, dropdown.getItemCount());
                        assertFalse(dropdown.isEnabled()); // Custom starter selection is still TODO.
                        assertEquals("Bulbasaur", dropdown.getItemAt(1));
                        for (int i = 1; i < dropdown.getItemCount(); i++) {
                            String text = dropdown.getItemAt(i).toString();
                            assertTrue(text.matches("[\\p{IsLatin}0-9 .’'♀♂-]+"), text);
                        }
                    }
                    ((JRadioButton) field(gui, "spRandomBasicRadioButton")).doClick();
                    assertTrue(((JRadioButton) field(gui, "spTypeNoneRadioButton")).isEnabled());
                    assertTrue(((JRadioButton) field(gui, "spTypeNoneRadioButton")).isSelected());
                    for (String name : List.of("spCustomRadioButton", "spRandomCompletelyRadioButton",
                            "spRandomTwoEvosRadioButton", "spTypeFwgRadioButton", "spTypeTriangleRadioButton",
                            "spTypeSingleRadioButton", "spTypeUniqueRadioButton", "spNoLegendariesCheckBox",
                            "spRandomizeStarterHeldItemsCheckBox", "tpRivalCarriesStarterCheckBox")) {
                        assertFalse(((JComponent) field(gui, name)).isEnabled(), name);
                    }
                    Settings imported = new Settings();
                    ((JRadioButton) field(gui, "igtRandomizeBothRequestedGivenRadioButton")).doClick();
                    for (String name : List.of("igtRandomizeNicknamesCheckBox", "igtRandomizeIVsCheckBox")) {
                        assertTrue(((JCheckBox) field(gui, name)).isEnabled(), name);
                        ((JCheckBox) field(gui, name)).doClick();
                    }
                    for (String name : List.of("igtRandomizeGivenPokemonOnlyRadioButton",
                            "igtRandomizeOTsCheckBox", "igtRandomizeItemsCheckBox")) {
                        assertFalse(((JComponent) field(gui, name)).isEnabled(), name);
                    }
                    ((JRadioButton) field(gui, "igtUnchangedRadioButton")).doClick();
                    assertFalse(((JCheckBox) field(gui, "igtRandomizeNicknamesCheckBox")).isEnabled());
                    imported.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST);
                    imported.setInGameTradesMod(Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED);
                    imported.setRandomizeInGameTradesNicknames(true);
                    imported.setRandomizeInGameTradesIVs(true);
                    imported.setRandomizeInGameTradesOTs(true);
                    imported.setRandomizeInGameTradesItems(true);
                    imported.setRandomizeWildPokemon(true);
                    imported.setRandomizeMovePPs(true);
                    imported.setStartersMod(Settings.StartersMod.RANDOM_BASIC);
                    imported.setStartersTypeMod(Settings.StartersTypeMod.FIRE_WATER_GRASS);
                    invoke(gui, "restoreStateFromSettings", Settings.class, imported);
                    invoke(gui, "enableOrDisableSubControls");
                    assertTrue(((JCheckBox) field(gui, "wpRandomizeWildPokemonCheckBox")).isSelected());
                    assertTrue(((JRadioButton) field(gui, "wpZoneNoneRadioButton")).isSelected());
                    for (String name : List.of("wpZoneNoneRadioButton", "wpTRNoneRadioButton",
                            "wpERNoneRadioButton", "wpCatchEmAllModeCheckBox")) {
                        assertTrue(((JComponent) field(gui, name)).isEnabled(), name);
                    }
                    assertFalse(((JComponent) field(gui, "wpZoneGameRadioButton")).isEnabled());
                    assertFalse(((JComponent) field(gui, "wpSetMinimumCatchRateCheckBox")).isEnabled());
                    assertTrue(((JCheckBox) field(gui, "mdRandomizeMovePPCheckBox")).isSelected());
                    var settingsMethod = RandomizerGUI.class.getDeclaredMethod("getCurrentSettings");
                    settingsMethod.setAccessible(true);
                    Settings exported = (Settings) settingsMethod.invoke(gui);
                    assertFalse(exported.isRandomizeIntroMon());
                    assertTrue(exported.isRandomizeWildPokemon());
                    assertTrue(exported.isRandomizeMovePPs());
                    assertEquals(Settings.StartersMod.RANDOM_BASIC, exported.getStartersMod());
                    assertEquals(Settings.StartersTypeMod.NONE, exported.getStartersTypeMod());
                    assertEquals(Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED, exported.getInGameTradesMod());
                    assertTrue(exported.isRandomizeInGameTradesNicknames());
                    assertTrue(exported.isRandomizeInGameTradesIVs());
                    assertFalse(exported.isRandomizeInGameTradesOTs());
                    assertFalse(exported.isRandomizeInGameTradesItems());
                    modern.setStarters(List.of(modern.getSpeciesForInternalId(417),
                            modern.getSpeciesForInternalId(454), modern.getSpeciesForInternalId(459)));
                    invoke(gui, "populateDropdowns");
                    assertEquals("Deoxys-Attack", ((JComboBox<?>) field(gui, "spComboBox1")).getSelectedItem());
                    assertEquals("Ursaluna-Bloodmoon", ((JComboBox<?>) field(gui, "spComboBox2")).getSelectedItem());
                    assertEquals("Articuno-Galarian", ((JComboBox<?>) field(gui, "spComboBox3")).getSelectedItem());
                    modern.setStarters(List.of(modern.getSpeciesForInternalId(277),
                            modern.getSpeciesForInternalId(280), modern.getSpeciesForInternalId(283)));
                    load(gui, retail);
                    Map<String, String> restored = snapshot(gui);
                    baseline.forEach((name, state) -> assertEquals(state, restored.get(name), name));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                try { ((JFrame) field(gui, "frame")).dispose(); }
                catch (Exception e) { throw new RuntimeException(e); }
            }
        });
    }

    private static void load(RandomizerGUI gui, RomHandler handler) throws Exception {
        invoke(gui, "initialState");
        Field f = RandomizerGUI.class.getDeclaredField("romHandler");
        f.setAccessible(true);
        f.set(gui, handler);
        invoke(gui, "populateDropdowns");
        invoke(gui, "romLoaded");
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void invoke(Object target, String name, Class<?> type, Object arg) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(target, arg);
    }

    private static Map<String, String> snapshot(RandomizerGUI gui) throws Exception {
        Map<String, String> states = new TreeMap<>();
        for (Field f : RandomizerGUI.class.getDeclaredFields()) {
            f.setAccessible(true);
            if (f.get(gui) instanceof JComponent control) {
                if (!availableInTabs(control)) continue;
                states.put(f.getName(), control.isEnabled() + "/" + control.isVisible() + "/"
                        + control.getToolTipText());
            }
        }
        return states;
    }

    private static boolean availableInTabs(Component component) {
        for (Component c = component; c != null; c = c.getParent()) {
            // Unselected tabs are still available; generation-specific hidden panels are not.
            if (!c.isVisible() && !(c.getParent() instanceof JTabbedPane)) return false;
        }
        return true;
    }

    /** Exercises the ordinary Gen 3 GUI path without distributing a retail ROM in the test suite. */
    private static class RetailGuiFixture extends Gen3RomHandler {
        private final ModernEmeraldRomHandler preview;
        RetailGuiFixture(ModernEmeraldRomHandler preview) {
            this.preview = preview;
            romEntry = Gen3RomEntry.READER.readFromString("""
                    [Emerald (U)]
                    Game=BPEE
                    Version=0
                    Type=Em
                    """).getFirst();
        }
        @Override public boolean isRomValid(java.io.PrintStream log) { return true; }
        @Override public List<Species> getSpecies() { return preview.getSpecies(); }
        @Override public List<Species> getSpeciesInclFormes() { return getSpecies(); }
        @Override public List<Species> getStarters() { return preview.getStarters(); }
        @Override public TypeTable getTypeTable() { return new TypeTable(Type.getAllTypes(3)); }
        @Override public boolean hasCustomPlayerGraphicsSupport() { return false; }
        @Override public boolean hasPokemonPaletteSupport() { return false; }
        @Override public int miscTweaksAvailable() { return 0; }
    }
}
