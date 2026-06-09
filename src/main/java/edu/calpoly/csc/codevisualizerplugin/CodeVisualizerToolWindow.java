package edu.calpoly.csc.codevisualizerplugin;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import edu.calpoly.csc.codevisualizerplugin.analysis.JavaFileMetric;
import edu.calpoly.csc.codevisualizerplugin.analysis.MetricLevel;
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
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
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
    private final Project project;
    private final JPanel content;
    private final JPanel gridPanel;
    private final JLabel gridStatusLabel;
    private final MetricsPlotPanel metricsPlotPanel;
    private final JLabel plantUmlImageLabel;
    private final JTextArea plantUmlOutput;
    private final ProjectPsiAnalyzer analyzer;
    private final PlantUmlRenderer plantUmlRenderer;
    private  javax.swing.JTable MetricTable;

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
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,20,5));
        legendPanel.setBorder(BorderFactory.createTitledBorder("Complexity Color"));

        JPanel low_green = new JPanel();
        low_green.setBackground(MetricLevel.LOW.color());
        low_green.setPreferredSize(new Dimension(20,20));
        legendPanel.add(low_green);
        legendPanel.add(new JLabel("Low Complexity"));

        JPanel medium_orange  = new JPanel();
        medium_orange.setBackground(MetricLevel.MEDIUM.color());
        medium_orange.setPreferredSize(new Dimension(20,20));
        legendPanel.add(medium_orange);
        legendPanel.add(new JLabel("Medium Complexity"));

        JPanel high_red = new JPanel();
        high_red.setBackground(MetricLevel.HIGH.color());
        high_red.setPreferredSize(new Dimension(20,20));
        legendPanel.add(high_red);
        legendPanel.add(new JLabel("High Complexity"));




        gridPanel.add(new JLabel("Grid tiles will appear here after analysis."));

        gridTab.add(header, BorderLayout.NORTH);
        gridTab.add(legendPanel,BorderLayout.AFTER_LAST_LINE);
        gridTab.add(new JScrollPane(gridPanel), BorderLayout.CENTER);
        return gridTab;
    }
    private JComponent createMetricsTab(List<JavaFileMetric> metrics){
        JPanel metrics_tab = new JPanel(new BorderLayout());
        String[] col_names = {"File","Classes","Methods","Constructors","Fields","Branches","Score"};
        Object[][] num = new Object[metrics.size()][7];

        for (int i = 0; i < metrics.size();i++){
            JavaFileMetric m = metrics.get(i);
            num[i][0] = m.fileName();
            num[i][1] = m.classCount();
            num[i][2] = m.methodCount();
            num[i][3] = m.constructorCount();
            num[i][4] = m.fieldCount();
            num[i][5] = m.branchCount();
            num[i][6] = m.score();
        }
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(num, col_names);
        MetricTable = new javax.swing.JTable(model);
        MetricTable.setRowHeight(30);
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(metricsPlotPanel),
                new JScrollPane(MetricTable)
        );
        splitPane.setResizeWeight(0.65);
        metrics_tab.add(splitPane,BorderLayout.CENTER);
        return  metrics_tab;
    }

    private JComponent createPlantUmlTab() {
        plantUmlImageLabel.setVerticalAlignment(SwingConstants.TOP);
        plantUmlImageLabel.setHorizontalAlignment(SwingConstants.LEFT);
        plantUmlImageLabel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel image = new JPanel(new BorderLayout());
        image.add(new JLabel("Render Diagram"), BorderLayout.NORTH);
        image.add(new JScrollPane(plantUmlImageLabel), BorderLayout.CENTER);
        JPanel PlantUML = new JPanel(new BorderLayout());
        PlantUML.add(new JLabel("PlantUML"),BorderLayout.NORTH);
        PlantUML.add(new JScrollPane(plantUmlOutput),BorderLayout.CENTER);
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                image,
                PlantUML
        );
        splitPane.setResizeWeight(0.72);
        return splitPane;
    }

    private void analyzeProject() {
        ProjectAnalysisResult result = ReadAction.compute(() -> analyzer.analyze(project));

        gridStatusLabel.setText(result.javaFileCount() + " Java files, "
                + result.totalClassCount() + " classes, "
                + result.totalMethodCount() + " methods, "
                + result.totalRelationshipCount() + " relationships");
        renderGrid(result.files());
        metricsPlotPanel.setMetrics(result.files());
        UpdateMetricsTable(result.files());
        renderPlantUml(result.plantUml());
    }
    private void UpdateMetricsTable(List<JavaFileMetric> metrics){
        String[] col_names = {"File","Classes","Methods","Constructors","Fields","Branches","Score"};
        Object[][] num = new Object[metrics.size()][7];

        for (int i = 0; i < metrics.size();i++){
            JavaFileMetric m = metrics.get(i);
            num[i][0] = m.fileName();
            num[i][1] = m.classCount();
            num[i][2] = m.methodCount();
            num[i][3] = m.constructorCount();
            num[i][4] = m.fieldCount();
            num[i][5] = m.branchCount();
            num[i][6] = m.score();
        }
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(num,col_names);
        MetricTable.setModel(model);
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
                + "Branches: " + metric.branchCount() + "</html>");
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
        private List<JavaFileMetric> metrics = List.of();

        private MetricsPlotPanel() {
            setPreferredSize(new Dimension(720, 480));
            setBackground(Color.WHITE);
        }

        void setMetrics(List<JavaFileMetric> metrics) {
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
            g2.drawString("Methods", right - 56, bottom + 28);
            g2.drawString("Fields + Branches", 8, top + 8);

            if (metrics.isEmpty()) {
                g2.drawString("Run Analyze Project to plot Java files.", left + 16, top + 32);
                g2.dispose();
                return;
            }

            int maxMethods = Math.max(1, metrics.stream().mapToInt(JavaFileMetric::methodCount).max().orElse(1));
            int maxWeight = Math.max(1, metrics.stream()
                    .mapToInt(metric -> metric.fieldCount() + metric.branchCount())
                    .max()
                    .orElse(1));

            for (JavaFileMetric metric : metrics) {
                int x = left + scale(metric.methodCount(), maxMethods, right - left);
                int y = bottom - scale(metric.fieldCount() + metric.branchCount(), maxWeight, bottom - top);
                int diameter = 12 + Math.min(20, metric.classCount() * 4);

                g2.setColor(metric.level().color());
                g2.fillOval(x - diameter / 2, y - diameter / 2, diameter, diameter);
                g2.setColor(new Color(32, 32, 32));
                drawFittedLabel(g2, metric.fileName(), x + 8, y - 8, right - x - 8);
            }

            g2.dispose();
        }

        private int scale(int value, int max, int length) {
            if (max == 0) {
                return 0;
            }
            return (int) Math.round((value / (double) max) * length);
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
