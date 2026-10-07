package dev.reactfuscator.gui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import dev.reactfuscator.config.*;
import dev.reactfuscator.core.ObfuscationManager;
import dev.reactfuscator.model.ObfuscationResult;
import dev.reactfuscator.registry.TransformerRegistry;
import dev.reactfuscator.service.*;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class WorkbenchPanel extends JPanel {
    private final ObfuscationManager manager;
    private final JTextField input = new JTextField(), output = new JTextField();
    private final JTextArea libraries = new JTextArea(3, 25),
            include = new JTextArea("**", 2, 25),
            exclude = new JTextArea(2, 25),
            keep = new JTextArea(2, 25);
    private final JComboBox<ProtectionProfile> profile =
            new JComboBox<>(ProtectionProfile.values());
    private final JCheckBox classNames = new JCheckBox("Classes", true),
            packageNames = new JCheckBox("Packages", true),
            methodNames = new JCheckBox("Methods", true),
            fieldNames = new JCheckBox("Fields", true);
    private final JCheckBox publicApi = new JCheckBox("Keep public API", false),
            serialization = new JCheckBox("Keep serialization ABI", false),
            mixins = new JCheckBox("Rename Mixins", true),
            scatter = new JCheckBox("Scatter packages", true);
    private final List<TransformerSettingsPanel> transformers = new ArrayList<>();
    private final JTextArea console = new JTextArea();
    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final JLabel status = new JLabel("Ready"),
            statistics = new JLabel("Classes —     Methods —     Fields —     Size —");
    private final JButton start = new JButton("Obfuscate"), cancel = new JButton("Cancel");
    private final JSpinner seed =
            new JSpinner(new SpinnerNumberModel(42L, Long.MIN_VALUE, Long.MAX_VALUE, 1L));
    private final JCheckBox fixedSeed = new JCheckBox("Reproducible seed");
    private ObfuscationConfig baseConfig = new ObfuscationConfig();
    private CancellationToken cancellation;
    private boolean running;

    public WorkbenchPanel(ObfuscationManager manager, TransformerRegistry registry) {
        this.manager = manager;
        setLayout(new BorderLayout(24, 20));
        setBorder(BorderFactory.createEmptyBorder(26, 30, 24, 30));
        setBackground(new Color(14, 14, 14));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel brand = new JPanel(new BorderLayout(12, 4));
        brand.setOpaque(false);
        JLabel logo = new JLabel(icon("shield", 36));
        brand.add(logo, BorderLayout.WEST);
        JLabel title = new JLabel("React-Fuscator");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 27));
        brand.add(title, BorderLayout.CENTER);
        JLabel subtitle = new JLabel("BYTECODE PROTECTION  /  JAVA · PAPER · FABRIC");
        subtitle.setForeground(new Color(145, 145, 145));
        subtitle.setFont(subtitle.getFont().deriveFont(10f));
        brand.add(subtitle, BorderLayout.SOUTH);
        header.add(brand, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton load = new JButton("Load config", icon("folder", 16)),
                save = new JButton("Save config", icon("save", 16));
        load.addActionListener(e -> loadConfig());
        save.addActionListener(e -> saveConfig());
        actions.add(load);
        actions.add(save);
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        JPanel left = new JPanel();
        left.setBackground(new Color(14, 14, 14));
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(section("01  /  ARTIFACT"));
        left.add(Box.createVerticalStrut(12));
        JPanel drop = new JPanel(new BorderLayout(0, 7));
        drop.setBackground(new Color(20, 20, 20));
        drop.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(65, 65, 65)),
                        BorderFactory.createEmptyBorder(22, 20, 22, 20)));
        drop.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));
        drop.add(new JLabel(icon("upload", 27), SwingConstants.CENTER), BorderLayout.NORTH);
        JLabel dropTitle = new JLabel("Drop a JAR to begin", SwingConstants.CENTER);
        dropTitle.setFont(dropTitle.getFont().deriveFont(Font.BOLD, 16));
        drop.add(dropTitle, BorderLayout.CENTER);
        drop.add(
                new JLabel("or select an artifact below", SwingConstants.CENTER),
                BorderLayout.SOUTH);
        drop.setTransferHandler(new JarDropHandler(this::selectInput));
        setTransferHandler(new JarDropHandler(this::selectInput));
        left.add(drop);
        left.add(Box.createVerticalStrut(12));
        left.add(fileRow("Input", input, false));
        left.add(Box.createVerticalStrut(8));
        left.add(fileRow("Output", output, true));
        left.add(Box.createVerticalStrut(18));
        left.add(section("02  /  PROTECTION"));
        left.add(Box.createVerticalStrut(10));
        profile.setSelectedItem(ProtectionProfile.EXTREME);
        profile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        left.add(profile);
        left.add(Box.createVerticalStrut(9));
        JPanel names = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        names.setOpaque(false);
        names.add(classNames);
        names.add(packageNames);
        names.add(methodNames);
        names.add(fieldNames);
        names.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        left.add(names);
        JPanel seedRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        seedRow.setOpaque(false);
        seedRow.add(fixedSeed);
        seed.setPreferredSize(new Dimension(160, 28));
        seedRow.add(seed);
        seedRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        left.add(seedRow);
        left.add(Box.createVerticalStrut(15));
        JPanel compatibility = new JPanel(new GridLayout(2, 2, 4, 3));
        compatibility.setOpaque(false);
        compatibility.add(publicApi);
        compatibility.add(serialization);
        compatibility.add(mixins);
        compatibility.add(scatter);
        compatibility.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        left.add(compatibility);
        left.add(Box.createVerticalStrut(8));
        JTabbedPane rules = new JTabbedPane();
        rules.addTab("Libraries", textBox(libraries, "One dependency JAR or directory per line"));
        rules.addTab("Include", textBox(include, "Internal-name globs, one per line"));
        rules.addTab("Exclude", textBox(exclude, "Classes / owner#method(descriptor)"));
        rules.addTab("Keep names", textBox(keep, "Names retained; code still transformed"));
        rules.setPreferredSize(new Dimension(430, 160));
        left.add(rules);
        left.add(Box.createVerticalGlue());
        JPanel right = new JPanel();
        right.setBackground(new Color(14, 14, 14));
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(section("03  /  TRANSFORMER PIPELINE"));
        right.add(Box.createVerticalStrut(12));
        for (var transformer : registry.ordered()) {
            var row = new TransformerSettingsPanel(transformer.descriptor());
            row.setPreferredSize(new Dimension(620, 52));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
            transformers.add(row);
            right.add(row);
            right.add(Box.createVerticalStrut(6));
        }
        JLabel hint = new JLabel("Every result is checked by ASM before it is written.");
        hint.setForeground(new Color(150, 150, 150));
        right.add(Box.createVerticalStrut(7));
        right.add(hint);
        right.add(Box.createVerticalGlue());
        for (Component component : left.getComponents()) {
            if (component instanceof JComponent child) {
                child.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }
        for (Component component : right.getComponents()) {
            if (component instanceof JComponent child) {
                child.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }
        JScrollPane transformerScroll = new JScrollPane(right);
        transformerScroll.setBorder(null);
        transformerScroll.getVerticalScrollBar().setUnitIncrement(16);
        transformerScroll.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        JSplitPane middle =
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollable(left), transformerScroll);
        middle.setBackground(new Color(14, 14, 14));
        middle.setBorder(null);
        middle.setResizeWeight(.46);
        middle.setDividerSize(20);
        middle.setContinuousLayout(true);
        add(middle, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 10));
        bottom.setOpaque(false);
        console.setEditable(false);
        console.setFont(new Font("Consolas", Font.PLAIN, 12));
        console.setBackground(new Color(9, 9, 9));
        console.setForeground(new Color(195, 195, 195));
        console.setText("React-Fuscator ready. Select a JAR and its dependency classpath.\n");
        JScrollPane scroll = new JScrollPane(console);
        scroll.setPreferredSize(new Dimension(100, 160));
        bottom.add(scroll, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(16, 9));
        footer.setOpaque(false);
        progress.setPreferredSize(new Dimension(100, 5));
        footer.add(progress, BorderLayout.NORTH);
        JPanel labels = new JPanel(new GridLayout(2, 1, 0, 6));
        labels.setOpaque(false);
        status.setForeground(Color.WHITE);
        statistics.setForeground(new Color(155, 155, 155));
        labels.add(status);
        labels.add(statistics);
        footer.add(labels, BorderLayout.CENTER);
        JPanel runButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        runButtons.setOpaque(false);
        cancel.setEnabled(false);
        cancel.addActionListener(e -> cancel());
        start.setIcon(icon("shield", 18));
        start.setBackground(Color.WHITE);
        start.setForeground(Color.BLACK);
        start.setFont(start.getFont().deriveFont(Font.BOLD));
        start.addActionListener(e -> run());
        runButtons.add(cancel);
        runButtons.add(start);
        footer.add(runButtons, BorderLayout.EAST);
        bottom.add(footer, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        profile.addActionListener(
                e ->
                        transformers.forEach(
                                t -> t.profile((ProtectionProfile) profile.getSelectedItem())));
        transformers.forEach(t -> t.profile(ProtectionProfile.EXTREME));
    }

    private JScrollPane scrollable(JPanel panel) {
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private Icon icon(String name, int size) {
        return new FlatSVGIcon("icons/" + name + ".svg", size, size);
    }

    private JLabel section(String title) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11));
        label.setForeground(new Color(170, 170, 170));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel textBox(JTextArea area, String hint) {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(9, 9, 9, 9));
        area.setFont(new Font("Consolas", Font.PLAIN, 12));
        panel.add(new JScrollPane(area));
        JLabel label = new JLabel(hint);
        label.setFont(label.getFont().deriveFont(11f));
        panel.add(label, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel fileRow(String title, JTextField text, boolean save) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JLabel label = new JLabel(title);
        label.setPreferredSize(new Dimension(48, 30));
        row.add(label, BorderLayout.WEST);
        row.add(text);
        JButton browse = new JButton(icon("folder", 17));
        browse.addActionListener(
                e -> {
                    JFileChooser chooser = chooser();
                    if ((save ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this))
                            == JFileChooser.APPROVE_OPTION) {
                        if (save) {
                            output.setText(chooser.getSelectedFile().getAbsolutePath());
                        } else {
                            selectInput(chooser.getSelectedFile().toPath());
                        }
                    }
                });
        row.add(browse, BorderLayout.EAST);
        return row;
    }

    private JFileChooser chooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Java archives (*.jar)", "jar"));
        return chooser;
    }

    private void selectInput(Path path) {
        if (running) {
            return;
        }
        input.setText(path.toAbsolutePath().toString());
        String name = path.getFileName().toString();
        output.setText(
                path.toAbsolutePath()
                        .resolveSibling(name.replaceFirst("(?i)\\.jar$", "") + "-protected.jar")
                        .toString());
    }

    private List<String> lines(JTextArea area) {
        return area.getText().lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private ObfuscationConfig config() {
        ObfuscationConfig c = new ConfigParser().copy(baseConfig);
        c.profile = (ProtectionProfile) profile.getSelectedItem();
        c.seed = fixedSeed.isSelected() ? ((Number) seed.getValue()).longValue() : null;
        c.libraries = new ArrayList<>(lines(libraries));
        c.include = new ArrayList<>(lines(include));
        c.exclude = new ArrayList<>(lines(exclude));
        c.keep = new ArrayList<>(lines(keep));
        c.preservePublicApi = publicApi.isSelected();
        c.preserveSerializationNames = serialization.isSelected();
        c.renameMixins = mixins.isSelected();
        c.scatterPackages = scatter.isSelected();
        c.renameClasses = classNames.isSelected();
        c.renamePackages = packageNames.isSelected();
        c.renameMethods = methodNames.isSelected();
        c.renameFields = fieldNames.isSelected();
        transformers.forEach(t -> c.transformers.put(t.id(), t.settings()));
        return c;
    }

    private void run() {
        if (running) {
            return;
        }
        if (input.getText().isBlank() || output.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Select input and output JAR paths.");
            return;
        }
        ObfuscationConfig config = config();
        Path source, target;
        try {
            source = Path.of(input.getText());
            target = Path.of(output.getText());
            new ConfigParser().validate(config);
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
            return;
        }
        running = true;
        start.setEnabled(false);
        cancel.setEnabled(true);
        progress.setValue(0);
        cancellation = new CancellationToken();
        CancellationToken token = cancellation;
        SwingWorker<ObfuscationResult, String> worker =
                new SwingWorker<>() {
                    @Override
                    protected ObfuscationResult doInBackground() throws Exception {
                        return manager.obfuscate(
                                source,
                                target,
                                config,
                                new ProgressListener() {
                                    public void log(String message) {
                                        publish(message);
                                    }

                                    public void progress(double fraction, String message) {
                                        SwingUtilities.invokeLater(
                                                () -> {
                                                    progress.setValue((int) (fraction * 1000));
                                                    status.setText(message);
                                                });
                                    }
                                },
                                token);
                    }

                    @Override
                    protected void process(List<String> messages) {
                        messages.forEach(s -> console.append(s + "\n"));
                        console.setCaretPosition(console.getDocument().getLength());
                    }

                    @Override
                    protected void done() {
                        running = false;
                        start.setEnabled(true);
                        cancel.setEnabled(false);
                        try {
                            ObfuscationResult result = get();
                            var s = result.statistics();
                            statistics.setText(
                                    "Classes "
                                            + s.classes
                                            + "  /  renamed "
                                            + s.renamedClasses
                                            + "     Methods "
                                            + s.renamedMethods
                                            + "     Fields "
                                            + s.renamedFields
                                            + "     "
                                            + String.format(
                                                    Locale.ROOT,
                                                    "%.1f → %.1f KiB  /  %.2fs",
                                                    s.inputBytes / 1024.0,
                                                    s.outputBytes / 1024.0,
                                                    s.elapsedMillis / 1000.0));
                            status.setText("Complete · " + result.output().getFileName());
                            console.append(
                                    "Mapping: "
                                            + result.mapping()
                                            + "\nReport: "
                                            + result.report()
                                            + "\n");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            status.setText("Interrupted");
                        } catch (ExecutionException e) {
                            Throwable cause = e.getCause();
                            status.setText(
                                    cause instanceof java.util.concurrent.CancellationException
                                            ? "Cancelled"
                                            : "Failed");
                            console.append("ERROR: " + cause.getMessage() + "\n");
                        }
                    }
                };
        worker.execute();
    }

    public void cancel() {
        if (cancellation != null) {
            cancellation.cancel();
        }
    }

    private void loadConfig() {
        if (running) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON config", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            baseConfig = new ConfigParser().read(chooser.getSelectedFile().toPath());
            profile.setSelectedItem(baseConfig.profile);
            libraries.setText(String.join("\n", baseConfig.libraries));
            include.setText(String.join("\n", baseConfig.include));
            exclude.setText(String.join("\n", baseConfig.exclude));
            keep.setText(String.join("\n", baseConfig.keep));
            publicApi.setSelected(baseConfig.preservePublicApi);
            serialization.setSelected(baseConfig.preserveSerializationNames);
            mixins.setSelected(baseConfig.renameMixins);
            scatter.setSelected(baseConfig.scatterPackages);
            classNames.setSelected(baseConfig.renameClasses);
            packageNames.setSelected(baseConfig.renamePackages);
            methodNames.setSelected(baseConfig.renameMethods);
            fieldNames.setSelected(baseConfig.renameFields);
            fixedSeed.setSelected(baseConfig.seed != null);
            if (baseConfig.seed != null) {
                seed.setValue(baseConfig.seed);
            }
            transformers.forEach(
                    t -> {
                        if (baseConfig.transformers.containsKey(t.id())) {
                            t.load(baseConfig.settings(t.id()), baseConfig.profile);
                        }
                    });
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this, e.getMessage(), "Config error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveConfig() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON config", "json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            new ConfigParser().write(chooser.getSelectedFile().toPath(), config());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this, e.getMessage(), "Config error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
