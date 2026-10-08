package dev.reactfuscator.gui;

import dev.reactfuscator.core.ApplicationFactory;

import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public final class GuiLauncher {
    private final ApplicationFactory factory;

    public GuiLauncher(ApplicationFactory factory) {
        this.factory = factory;
    }

    public void open() {
        SwingUtilities.invokeLater(
                () -> {
                    new ThemeService().apply();
                    var registry = factory.registry();
                    WorkbenchPanel panel = new WorkbenchPanel(factory.manager(registry), registry);
                    JFrame frame = new JFrame("React-Fuscator");
                    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                    frame.setContentPane(panel);
                    frame.setMinimumSize(new Dimension(1080, 800));
                    frame.setSize(1240, 900);
                    frame.setLocationRelativeTo(null);
                    frame.addWindowListener(
                            new java.awt.event.WindowAdapter() {
                                @Override
                                public void windowClosing(java.awt.event.WindowEvent e) {
                                    panel.cancel();
                                    frame.dispose();
                                }
                            });
                    frame.setVisible(true);
                });
    }
}
