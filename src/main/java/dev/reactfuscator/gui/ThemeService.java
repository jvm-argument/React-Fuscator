package dev.reactfuscator.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

/** One palette shared by the desktop window and its preview renderer. */
public final class ThemeService {
    public void apply() {
        FlatDarkLaf.setup();
        UIManager.put("Panel.background", new Color(14,14,14));
        UIManager.put("TextField.background", new Color(23,23,23));
        UIManager.put("TextArea.background", new Color(23,23,23));
        UIManager.put("Button.background", new Color(35,35,35));
        UIManager.put("ComboBox.background", new Color(35,35,35));
        UIManager.put("Spinner.background", new Color(35,35,35));
        UIManager.put("Button.arc",12);
        UIManager.put("Component.arc",10);
        UIManager.put("TextComponent.arc",10);
        UIManager.put("Component.focusColor",Color.WHITE);
        UIManager.put("Component.accentColor",Color.WHITE);
        UIManager.put("TabbedPane.underlineColor",Color.WHITE);
        UIManager.put("TabbedPane.focusColor",new Color(55,55,55));
        UIManager.put("ProgressBar.foreground",Color.WHITE);
        UIManager.put("CheckBox.icon.selectedBackground",Color.WHITE);
        UIManager.put("CheckBox.icon.checkmarkColor",Color.BLACK);
        UIManager.put("defaultFont",new Font("Segoe UI",Font.PLAIN,13));
    }
}
