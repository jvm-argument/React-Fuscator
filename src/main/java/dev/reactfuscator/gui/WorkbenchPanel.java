package dev.reactfuscator.gui;

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
    private final WorkbenchViewFactory view;
    private final JTextField input = new JTextField();
    private final JTextField output = new JTextField();

    private final JTextArea libraries = new JTextArea(3, 25);
    private final JTextArea include = new JTextArea("**", 2, 25);
    private final JTextArea exclude = new JTextArea(2, 25);
    private final JTextArea keep = new JTextArea(2, 25);

    private final JComboBox<ProtectionProfile> profile =
            new JComboBox<>(ProtectionProfile.values());
    private final JCheckBox classNames = new JCheckBox("Classes", true);
    private final JCheckBox packageNames = new JCheckBox("Packages", true);
    private final JCheckBox methodNames = new JCheckBox("Methods", true);
    private final JCheckBox fieldNames = new JCheckBox("Fields", true);

    private final JCheckBox publicApi = new JCheckBox("Keep public API", false);
    private final JCheckBox serialization = new JCheckBox("Keep serialization ABI", false);
    private final JCheckBox mixins = new JCheckBox("Rename Mixins", true);
    private final JCheckBox scatter = new JCheckBox("Scatter packages", true);

    private final List<TransformerSettingsPanel> transformers = new ArrayList<>();
    private final JTextArea console = new JTextArea();
    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final JLabel status = new JLabel("Ready");
    private final JLabel statistics = new JLabel("Classes —     Methods —     Fields —     Size —");

    private final JButton start = new JButton("Obfuscate");
    private final JButton cancel = new JButton("Cancel");

    private final JSpinner seed =
            new JSpinner(new SpinnerNumberModel(42L, Long.MIN_VALUE, Long.MAX_VALUE, 1L));
    private final JCheckBox fixedSeed = new JCheckBox("Reproducible seed");
    private ObfuscationConfig baseConfig = new ObfuscationConfig();
    private CancellationToken cancellation;
    private boolean running;

    public WorkbenchPanel(ObfuscationManager manager, TransformerRegistry registry) {
        this(manager, registry, new WorkbenchViewFactory());
    }

    public WorkbenchPanel(
            ObfuscationManager manager, TransformerRegistry registry, WorkbenchViewFactory view) {
        this.manager = manager;
        this.view = view;
        setLayout(new BorderLayout(24, 20));
        setBorder(BorderFactory.createEmptyBorder(26, 30, 24, 30));
        setBackground(new Color(14, 14, 14));
        add(view.header(this::loadConfig, this::saveConfig), BorderLayout.NORTH);
        JPanel left = new JPanel();
        left.setBackground(new Color(14, 14, 14));
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(view.section("01  /  ARTIFACT"));
        left.add(Box.createVerticalStrut(12));
        JPanel drop = new JPanel(new BorderLayout(0, 7));
        drop.setBackground(new Color(20, 20, 20));
        drop.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(65, 65, 65)),
                        BorderFactory.createEmptyBorder(22, 20, 22, 20)));
        drop.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));
        drop.add(new JLabel(view.icon("upload", 27), SwingConstants.CENTER), BorderLayout.NORTH);
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
        left.add(view.section("02  /  PROTECTION"));
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
        rules.addTab(
                "Libraries", view.textBox(libraries, "One dependency JAR or directory per line"));
        rules.addTab("Include", view.textBox(include, "Internal-name globs, one per line"));
        rules.addTab("Exclude", view.textBox(exclude, "Classes / owner#method(descriptor)"));
        rules.addTab("Keep names", view.textBox(keep, "Names retained; code still transformed"));
        rules.setPreferredSize(new Dimension(430, 160));
        left.add(rules);
        left.add(Box.createVerticalGlue());
        JPanel right = view.transformerList(registry, transformers);
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
                new JSplitPane(
                        JSplitPane.HORIZONTAL_SPLIT, view.scrollable(left), transformerScroll);
        middle.setBackground(new Color(14, 14, 14));
        middle.setBorder(null);
        middle.setResizeWeight(.46);
        middle.setDividerSize(20);
        middle.setContinuousLayout(true);
        add(middle, BorderLayout.CENTER);
        add(
                view.footer(
                        console,
                        progress,
                        status,
                        statistics,
                        start,
                        cancel,
                        this::run,
                        this::cancel),
                BorderLayout.SOUTH);
        profile.addActionListener(
                e ->
                        transformers.forEach(
                                t -> t.profile((ProtectionProfile) profile.getSelectedItem())));
        transformers.forEach(t -> t.profile(ProtectionProfile.EXTREME));
    }

    private JPanel fileRow(String title, JTextField text, boolean save) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JLabel label = new JLabel(title);
        label.setPreferredSize(new Dimension(48, 30));
        row.add(label, BorderLayout.WEST);
        row.add(text);
        JButton browse = new JButton(view.icon("folder", 17));
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
