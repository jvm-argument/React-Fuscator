package dev.reactfuscator.gui;

import dev.reactfuscator.config.*;
import dev.reactfuscator.transform.TransformerDescriptor;

import java.awt.*;
import java.util.Arrays;

import javax.swing.*;

public final class TransformerSettingsPanel extends JPanel {
    private final TransformerDescriptor descriptor;
    private final JCheckBox enabled;
    private final JSpinner density = new JSpinner(new SpinnerNumberModel(35, 0, 100, 5));
    private final JSpinner rounds = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));
    private final JTextField exclusions = new JTextField();

    public TransformerSettingsPanel(TransformerDescriptor descriptor) {
        this.descriptor = descriptor;
        setLayout(new BorderLayout(16, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        setBackground(new Color(23, 23, 23));
        enabled = new JCheckBox(descriptor.name());
        enabled.setOpaque(false);
        enabled.setFont(enabled.getFont().deriveFont(Font.BOLD, 13));
        enabled.setToolTipText(descriptor.description());
        add(enabled, BorderLayout.WEST);
        JPanel settings = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        settings.setOpaque(false);
        if (descriptor.densityConfigurable()) {
            settings.add(new JLabel("Density"));
            density.setPreferredSize(new Dimension(70, 28));
            settings.add(density);
        }
        if (descriptor.roundsConfigurable()) {
            settings.add(new JLabel("Rounds"));
            rounds.setPreferredSize(new Dimension(58, 28));
            settings.add(rounds);
        }
        JButton detail =
                new JButton(
                        new com.formdev.flatlaf.extras.FlatSVGIcon("icons/settings.svg", 16, 16));
        detail.setToolTipText("Per-transformer exclusions and explanation");
        detail.addActionListener(
                e -> {
                    JPanel form = new JPanel(new BorderLayout(0, 12));
                    JTextArea explanation = new JTextArea(descriptor.description());
                    explanation.setWrapStyleWord(true);
                    explanation.setLineWrap(true);
                    explanation.setEditable(false);
                    explanation.setOpaque(false);
                    explanation.setColumns(40);
                    form.add(explanation, BorderLayout.NORTH);
                    JPanel row = new JPanel(new BorderLayout(0, 6));
                    row.add(
                            new JLabel("Exclude globs (comma-separated owner#method patterns)"),
                            BorderLayout.NORTH);
                    row.add(exclusions);
                    form.add(row);
                    JOptionPane.showMessageDialog(
                            this, form, descriptor.name(), JOptionPane.PLAIN_MESSAGE);
                });
        settings.add(detail);
        add(settings, BorderLayout.CENTER);
    }

    public void profile(ProtectionProfile profile) {
        enabled.setSelected(descriptor.defaultEnabled(profile));
        density.setValue(profile.density());
        rounds.setValue(profile.rounds());
    }

    public String id() {
        return descriptor.id();
    }

    public TransformerSettings settings() {
        TransformerSettings s = new TransformerSettings();
        s.enabled = enabled.isSelected();
        s.density = descriptor.densityConfigurable() ? (Integer) density.getValue() : -1;
        s.rounds = descriptor.roundsConfigurable() ? (Integer) rounds.getValue() : -1;
        s.exclude =
                Arrays.stream(exclusions.getText().split(","))
                        .map(String::strip)
                        .filter(t -> !t.isEmpty())
                        .toList();
        return s;
    }

    public void load(TransformerSettings s, ProtectionProfile p) {
        enabled.setSelected(s.enabled);
        density.setValue(s.density(p));
        rounds.setValue(s.rounds(p));
        exclusions.setText(String.join(", ", s.exclude));
    }
}
