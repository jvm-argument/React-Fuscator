package dev.reactfuscator.testing;

import dev.reactfuscator.core.ApplicationFactory;
import dev.reactfuscator.gui.ThemeService;
import dev.reactfuscator.gui.WorkbenchPanel;

import java.awt.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.*;

public final class GuiSmokeRunner {
    public static void main(String[] args) throws Exception {
        AtomicReference<WorkbenchPanel> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        new ThemeService().apply();
                        var factory = new ApplicationFactory();
                        var registry = factory.registry();
                        WorkbenchPanel panel =
                                new WorkbenchPanel(factory.manager(registry), registry);
                        view.set(panel);
                        field(panel, "input", JTextField.class)
                                .setText(Path.of(args[0]).toAbsolutePath().toString());
                        field(panel, "output", JTextField.class)
                                .setText(Path.of(args[1]).toAbsolutePath().toString());
                        field(panel, "libraries", JTextArea.class)
                                .setText(Path.of(args[2]).toAbsolutePath().toString());
                        field(panel, "start", JButton.class).doClick();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
        long deadline = System.nanoTime() + 30_000_000_000L;
        AtomicReference<String> status = new AtomicReference<>();
        do {
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            status.set(field(view.get(), "status", JLabel.class).getText());
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    });
            if (status.get().startsWith("Complete")) {
                break;
            }
            if (status.get().equals("Failed")) {
                throw new IllegalStateException("GUI processing failed");
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        if (!status.get().startsWith("Complete")) {
            throw new IllegalStateException("GUI processing timed out");
        }
        for (Path path :
                java.util.List.of(
                        Path.of(args[1]),
                        Path.of(args[1] + ".mapping.json"),
                        Path.of(args[1] + ".report.json"))) {
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Missing GUI artifact: " + path);
            }
        }
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        WorkbenchPanel panel = view.get();
                        int progress = field(panel, "progress", JProgressBar.class).getValue();
                        if (progress != 1000) {
                            throw new IllegalStateException(
                                    "GUI progress did not finish: " + progress);
                        }
                        System.out.println(status.get());
                        System.out.println(field(panel, "statistics", JLabel.class).getText());
                        Files.writeString(
                                Path.of(args[1] + ".gui.txt"),
                                field(panel, "console", JTextArea.class).getText());
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    private static <T> T field(WorkbenchPanel panel, String name, Class<T> type)
            throws ReflectiveOperationException {
        var field = WorkbenchPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(panel));
    }
}
