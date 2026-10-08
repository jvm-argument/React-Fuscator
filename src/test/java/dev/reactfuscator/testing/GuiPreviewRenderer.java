package dev.reactfuscator.testing;

import dev.reactfuscator.core.ApplicationFactory;
import dev.reactfuscator.gui.ThemeService;
import dev.reactfuscator.gui.WorkbenchPanel;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

public final class GuiPreviewRenderer {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    new ThemeService().apply();
                    ApplicationFactory factory = new ApplicationFactory();
                    var registry = factory.registry();
                    WorkbenchPanel panel = new WorkbenchPanel(factory.manager(registry), registry);
                    panel.setSize(1240, 860);
                    layout(panel);
                    BufferedImage image =
                            new BufferedImage(
                                    panel.getWidth(),
                                    panel.getHeight(),
                                    BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics();
                    panel.printAll(graphics);
                    graphics.dispose();
                    try {
                        Path path = Path.of(args[0]);
                        Files.createDirectories(path.toAbsolutePath().getParent());
                        ImageIO.write(image, "png", path.toFile());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container child) {
                layout(child);
            }
        }
    }
}
