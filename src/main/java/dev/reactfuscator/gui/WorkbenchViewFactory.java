package dev.reactfuscator.gui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import dev.reactfuscator.registry.TransformerRegistry;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

public final class WorkbenchViewFactory {
    public JPanel header(Runnable loadAction, Runnable saveAction) {
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
        load.addActionListener(e -> loadAction.run());
        save.addActionListener(e -> saveAction.run());
        actions.add(load);
        actions.add(save);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    public JPanel transformerList(
            TransformerRegistry registry, List<TransformerSettingsPanel> transformers) {
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
        return right;
    }

    public JPanel footer(
            JTextArea console,
            JProgressBar progress,
            JLabel status,
            JLabel statistics,
            JButton start,
            JButton cancel,
            Runnable startAction,
            Runnable cancelAction) {
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
        cancel.addActionListener(e -> cancelAction.run());
        start.setIcon(icon("shield", 18));
        start.setBackground(Color.WHITE);
        start.setForeground(Color.BLACK);
        start.setFont(start.getFont().deriveFont(Font.BOLD));
        start.addActionListener(e -> startAction.run());
        runButtons.add(cancel);
        runButtons.add(start);
        footer.add(runButtons, BorderLayout.EAST);
        bottom.add(footer, BorderLayout.SOUTH);
        return bottom;
    }

    public JScrollPane scrollable(JPanel panel) {
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    public Icon icon(String name, int size) {
        return new FlatSVGIcon("icons/" + name + ".svg", size, size);
    }

    public JLabel section(String title) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11));
        label.setForeground(new Color(170, 170, 170));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    public JPanel textBox(JTextArea area, String hint) {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(9, 9, 9, 9));
        area.setFont(new Font("Consolas", Font.PLAIN, 12));
        panel.add(new JScrollPane(area));
        JLabel label = new JLabel(hint);
        label.setFont(label.getFont().deriveFont(11f));
        panel.add(label, BorderLayout.SOUTH);
        return panel;
    }
}
