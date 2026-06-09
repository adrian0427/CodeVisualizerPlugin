package edu.calpoly.csc.codevisualizerplugin;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import edu.calpoly.csc.codevisualizerplugin.analysis.JavaFileMetric;
import edu.calpoly.csc.codevisualizerplugin.analysis.MetricLevel;
import edu.calpoly.csc.codevisualizerplugin.analysis.PackageMetric;
import edu.calpoly.csc.codevisualizerplugin.analysis.ProjectAnalysisResult;
import edu.calpoly.csc.codevisualizerplugin.analysis.ProjectPsiAnalyzer;
import edu.calpoly.csc.codevisualizerplugin.diagram.PlantUmlRenderer;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import java.util.List;

final class CodeVisualizerToolWindow {
    private static final String[] METRIC_TABLE_COLUMNS = {
            "Package",
            "Classes",
            "Abstract",
            "Incoming",
            "Outgoing",
            "A",
            "I",
            "D"
    };

    private final Project project;
    private final JPanel content;
    private final JPanel gridPanel;
    private final JLabel gridStatusLabel;
    private final MetricsPlotPanel metricsPlotPanel;
    private final JLabel plantUmlImageLabel;
    private final JTextArea plantUmlOutput;
    private final ProjectPsiAnalyzer analyzer;
    private final PlantUmlRenderer plantUmlRenderer;
    private JTable metricTable;

    CodeVisualizerToolWindow(Project project) {
        this.project = project;
        this.content = new JPanel(new BorderLayout());
        this.gridPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        this.gridStatusLabel = new JLabel("No analysis has been run yet.");
        this.metricsPlotPanel = new MetricsPlotPanel();
        this.plantUmlImageLabel = new JLabel("Run Analyze Project to render the diagram.", SwingConstants.CENTER);
        this.plantUmlOutput = createReadOnlyTextArea("@startuml\n' PlantUML diagram will appear here after analysis.\n@enduml");
        this.analyzer = new ProjectPsiAnalyzer();
        this.plantUmlRenderer = new PlantUmlRenderer();

        content.add(createTabs(), BorderLayout.CENTER);
    }

    JComponent getContent() {
        return content;
    }

    private JTabbedPane createTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Grid", createGridTab());
        tabs.addTab("Metrics", createMetricsTab(List.of()));
        tabs.addTab("PlantUML", createPlantUmlTab());

        return tabs;
    }

    private JComponent createGridTab() {
        JPanel gridTab = new JPanel(new BorderLayout(8, 8));
        JButton analyzeButton = new JButton("Analyze Project");
        analyzeButton.addActionListener(event -> analyzeProject());

        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.add(analyzeButton, BorderLayout.WEST);
        header.add(gridStatusLabel, BorderLayout.CENTER);
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        legendPanel.setBorder(BorderFactory.createTitledBorder("Complexity Color"));

        JPanel lowGreen = createLegendSwatch(MetricLevel.LOW);
        legendPanel.add(lowGreen);
        legendPanel.add(new JLabel("Low Complexity"));

        JPanel mediumOrange = createLegendSwatch(MetricLevel.MEDIUM);
        legendPanel.add(mediumOrange);
        legendPanel.add(new JLabel("Medium Complexity"));

        JPanel highRed = createLegendSwatch(MetricLevel.HIGH);
        legendPanel.add(highRed);
        legendPanel.add(new JLabel("High Complexity"));

        gridPanel.add(new JLabel("Grid tiles will appear here after analysis."));

        gridTab.add(header, BorderLayout.NORTH);
        gridTab.add(legendPanel, BorderLayout.SOUTH);
        gridTab.add(new JScrollPane(gridPanel), BorderLayout.CENTER);
        return gridTab;
    }

    private JPanel createLegendSwatch(MetricLevel metricLevel) {
        JPanel swatch = new JPanel();
        swatch.setBackground(metricLevel.color());
        swatch.setPreferredSize(new Dimension(20, 20));
        return swatch;
    }

    private JComponent createMetricsTab(List<PackageMetric> metrics) {
        JPanel metricsTab = new JPanel(new BorderLayout());
        metricTable = new JTable(createMetricTableModel(metrics));
        metricTable.setAutoCreateRowSorter(true);
        metricTable.setRowHeight(30);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(metricsPlotPanel),
                new JScrollPane(metricTable)
        );
        splitPane.setResizeWeight(0.65);
        metricsTab.add(splitPane, BorderLayout.CENTER);
        return metricsTab;
    }

    private JComponent createPlantUmlTab() {
        plantUmlImageLabel.setVerticalAlignment(SwingConstants.TOP);
        plantUmlImageLabel.setHorizontalAlignment(SwingConstants.LEFT);
        plantUmlImageLabel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel image = new JPanel(new BorderLayout());
        image.add(new JLabel("Render Diagram"), BorderLayout.NORTH);
        image.add(new JScrollPane(plantUmlImageLabel), BorderLayout.CENTER);
        JPanel plantUml = new JPanel(new BorderLayout());
        plantUml.add(new JLabel("PlantUML"), BorderLayout.NORTH);
        plantUml.add(new JScrollPane(plantUmlOutput), BorderLayout.CENTER);
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                image,
                plantUml
        );
        splitPane.setResizeWeight(0.72);
        return splitPane;
    }

    private void analyzeProject() {
        ProjectAnalysisResult result = ReadAction.compute(() -> analyzer.analyze(project));

        gridStatusLabel.setText(result.javaFileCount() + " Java files, "
                + result.totalClassCount() + " classes, "
                + result.totalMethodCount() + " methods, "
                + result.totalLinesOfCode() + " LOC, "
                + result.totalRelationshipCount() + " relationships");
        renderGrid(result.files());
        metricsPlotPanel.setMetrics(result.packages());
        updateMetricsTable(result.packages());
        renderPlantUml(result.plantUml());
    }

    private void updateMetricsTable(List<PackageMetric> metrics) {
        metricTable.setModel(createMetricTableModel(metrics));
    }

    private DefaultTableModel createMetricTableModel(List<PackageMetric> metrics) {
        Object[][] rows = new Object[metrics.size()][METRIC_TABLE_COLUMNS.length];

        for (int i = 0; i < metrics.size(); i++) {
            PackageMetric metric = metrics.get(i);
            rows[i][0] = metric.packageName();
            rows[i][1] = metric.classCount();
            rows[i][2] = metric.abstractClassCount();
            rows[i][3] = metric.incomingDependencies();
            rows[i][4] = metric.outgoingDependencies();
            rows[i][5] = formatMetric(metric.abstractness());
            rows[i][6] = formatMetric(metric.instability());
            rows[i][7] = formatMetric(metric.distanceFromMainSequence());
        }

        return new DefaultTableModel(rows, METRIC_TABLE_COLUMNS);
    }

    private String formatMetric(double value) {
        return String.format("%.2f", value);
    }

    private void renderPlantUml(String plantUml) {
        plantUmlOutput.setText(plantUml);
        plantUmlOutput.setCaretPosition(0);

        try {
            ImageIcon diagram = plantUmlRenderer.renderPng(plantUml);
            plantUmlImageLabel.setText(null);
            plantUmlImageLabel.setIcon(diagram);
        } catch (IOException exception) {
            plantUmlImageLabel.setIcon(null);
            plantUmlImageLabel.setText("PlantUML render failed: " + exception.getMessage());
        }
    }

    private void renderGrid(List<JavaFileMetric> metrics) {
        gridPanel.removeAll();

        if (metrics.isEmpty()) {
            gridPanel.add(new JLabel("No Java files found in this project."));
        }

        for (JavaFileMetric metric : metrics) {
            gridPanel.add(createMetricTile(metric));
        }

        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private JButton createMetricTile(JavaFileMetric metric) {
        JButton tile = new JButton("<html><b>" + metric.fileName() + "</b><br/>"
                + "Classes: " + metric.classCount() + "<br/>"
                + "Methods: " + metric.methodCount() + "<br/>"
                + "Constructors: " + metric.constructorCount() + "<br/>"
                + "LOC: " + metric.linesOfCode() + "<br/>"
                + "Rough CC: " + metric.roughCyclomaticComplexity() + "</html>");
        tile.setToolTipText(metric.relativePath());
        tile.setPreferredSize(new Dimension(170, 110));
        tile.setBackground(metric.level().color());
        tile.setForeground(Color.WHITE);
        tile.setFocusPainted(false);
        tile.setOpaque(true);
        tile.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tile.addActionListener(event -> FileEditorManager.getInstance(project).openFile(metric.virtualFile(), true));
        return tile;
    }

    private static JTextArea createReadOnlyTextArea(String text) {
        JTextArea textArea = new JTextArea(text);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        return textArea;
    }

    private static final class MetricsPlotPanel extends JPanel {
        private List<PackageMetric> metrics = List.of();

        private MetricsPlotPanel() {
            setPreferredSize(new Dimension(720, 480));
            setBackground(Color.WHITE);
        }

        void setMetrics(List<PackageMetric> metrics) {
            this.metrics = List.copyOf(metrics);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int left = 56;
            int top = 32;
            int right = getWidth() - 32;
            int bottom = getHeight() - 48;

            g2.setColor(new Color(64, 64, 64));
            g2.drawLine(left, bottom, right, bottom);
            g2.drawLine(left, bottom, left, top);
            g2.drawString("Instability (I)", right - 86, bottom + 28);
            g2.drawString("Abstractness (A)", 8, top + 8);
            g2.drawString("main sequence", left + 12, bottom - 12);
            g2.drawLine(left, bottom, right, top);

            if (metrics.isEmpty()) {
                g2.drawString("Run Analyze Project to plot packages.", left + 16, top + 32);
                g2.dispose();
                return;
            }

            for (PackageMetric metric : metrics) {
                int x = left + scale(metric.instability(), right - left);
                int y = bottom - scale(metric.abstractness(), bottom - top);
                int diameter = 12 + Math.min(20, metric.classCount() * 4);

                g2.setColor(distanceColor(metric.distanceFromMainSequence()));
                g2.fillOval(x - diameter / 2, y - diameter / 2, diameter, diameter);
                g2.setColor(new Color(32, 32, 32));
                drawFittedLabel(g2, metric.packageName(), x + 8, y - 8, right - x - 8);
            }

            g2.dispose();
        }

        private int scale(double value, int length) {
            return (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * length);
        }

        private Color distanceColor(double distance) {
            if (distance >= 0.67) {
                return MetricLevel.HIGH.color();
            }
            if (distance >= 0.34) {
                return MetricLevel.MEDIUM.color();
            }
            return MetricLevel.LOW.color();
        }

        private void drawFittedLabel(Graphics2D g2, String text, int x, int y, int maxWidth) {
            if (maxWidth <= 16) {
                return;
            }
            FontMetrics metrics = g2.getFontMetrics();
            String label = text;
            while (label.length() > 4 && metrics.stringWidth(label) > maxWidth) {
                label = label.substring(0, label.length() - 4) + "...";
            }
            g2.drawString(label, x, y);
        }
    }
}
