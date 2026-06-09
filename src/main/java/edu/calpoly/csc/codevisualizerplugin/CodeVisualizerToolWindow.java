package edu.calpoly.csc.codevisualizerplugin;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
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
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
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
        metricTable = createMetricTable(createMetricTableModel(metrics));
        metricTable.setAutoCreateRowSorter(true);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(metricsPlotPanel),
                new JScrollPane(metricTable)
        );
        splitPane.setResizeWeight(0.58);
        metricsTab.add(splitPane, BorderLayout.CENTER);
        return metricsTab;
    }

    private JTable createMetricTable(DefaultTableModel model) {
        JTable table = new JTable(model) {
            @Override
            public String getToolTipText(MouseEvent event) {
                Point point = event.getPoint();
                int row = rowAtPoint(point);
                int column = columnAtPoint(point);
                if (row >= 0 && column == 0) {
                    Object value = getValueAt(row, column);
                    return value == null ? null : value.toString();
                }
                return super.getToolTipText(event);
            }
        };
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        configureMetricTableColumns(table);
        return table;
    }

    private void configureMetricTableColumns(JTable table) {
        TableColumnModel columns = table.getColumnModel();
        columns.getColumn(0).setPreferredWidth(220);
        for (int i = 1; i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setPreferredWidth(72);
        }
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
        gridStatusLabel.setText("Analyzing project...");
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Analyzing Code Visualizer Project", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    ProjectAnalysisResult result = ReadAction.compute(() -> analyzer.analyze(project));
                    RenderedPlantUml renderedPlantUml = renderPlantUmlInBackground(result.plantUml());
                    javax.swing.SwingUtilities.invokeLater(() -> applyAnalysisResult(result, renderedPlantUml));
                } catch (RuntimeException exception) {
                    javax.swing.SwingUtilities.invokeLater(() -> showAnalysisError(exception));
                }
            }
        });
    }

    private RenderedPlantUml renderPlantUmlInBackground(String plantUml) {
        try {
            return new RenderedPlantUml(plantUmlRenderer.renderPng(plantUml), null);
        } catch (IOException exception) {
            return new RenderedPlantUml(null, exception);
        }
    }

    private void applyAnalysisResult(ProjectAnalysisResult result, RenderedPlantUml renderedPlantUml) {
        gridStatusLabel.setText(result.javaFileCount() + " Java files, "
                + result.totalClassCount() + " classes, "
                + result.totalMethodCount() + " methods, "
                + result.totalLinesOfCode() + " LOC, "
                + result.totalRelationshipCount() + " relationships");
        renderGrid(result.files());
        metricsPlotPanel.setMetrics(result.packages());
        updateMetricsTable(result.packages());
        renderPlantUml(result.plantUml(), renderedPlantUml);
    }

    private void showAnalysisError(RuntimeException exception) {
        gridStatusLabel.setText("Analysis failed: " + exception.getMessage());
        plantUmlImageLabel.setIcon(null);
        plantUmlImageLabel.setText("Analysis failed. See IDE logs for details.");
    }

    private void updateMetricsTable(List<PackageMetric> metrics) {
        metricTable.setModel(createMetricTableModel(metrics));
        configureMetricTableColumns(metricTable);
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

    private void renderPlantUml(String plantUml, RenderedPlantUml renderedPlantUml) {
        plantUmlOutput.setText(plantUml);
        plantUmlOutput.setCaretPosition(0);

        if (renderedPlantUml.diagram() != null) {
            plantUmlImageLabel.setText(null);
            plantUmlImageLabel.setIcon(renderedPlantUml.diagram());
        } else {
            plantUmlImageLabel.setIcon(null);
            plantUmlImageLabel.setText("PlantUML render failed: " + renderedPlantUml.error().getMessage());
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

    private record RenderedPlantUml(ImageIcon diagram, IOException error) {
    }

    private static final class MetricsPlotPanel extends JPanel {
        private static final int LEFT_PADDING = 72;
        private static final int TOP_PADDING = 44;
        private static final int RIGHT_PADDING = 40;
        private static final int BOTTOM_PADDING = 70;

        private List<PackageMetric> metrics = List.of();

        private MetricsPlotPanel() {
            setPreferredSize(new Dimension(720, 340));
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

            int left = LEFT_PADDING;
            int top = TOP_PADDING;
            int right = getWidth() - RIGHT_PADDING;
            int bottom = getHeight() - BOTTOM_PADDING;

            g2.setColor(new Color(64, 64, 64));
            g2.drawLine(left, bottom, right, bottom);
            g2.drawLine(left, bottom, left, top);
            drawAxisTicks(g2, left, top, right, bottom);
            g2.drawString("Instability (I)", Math.max(left, right - 100), bottom + 44);
            g2.drawString("Abstractness (A)", 12, top - 12);
            g2.drawString("main sequence", left + 12, bottom - 12);
            g2.drawLine(left, bottom, right, top);
            drawLegend(g2, left, getHeight() - 22);

            if (metrics.isEmpty()) {
                g2.drawString("Run Analyze Project to plot packages.", left + 16, top + 32);
                g2.dispose();
                return;
            }

            for (PackageMetric metric : metrics) {
                int x = left + scale(metric.instability(), right - left);
                int y = bottom - scale(metric.abstractness(), bottom - top);
                int diameter = 12 + Math.min(20, metric.classCount() * 4);
                int labelX = Math.min(x + 8, right - 120);
                int labelY = Math.max(top + 14, y - 8);

                g2.setColor(distanceColor(metric.distanceFromMainSequence()));
                g2.fillOval(x - diameter / 2, y - diameter / 2, diameter, diameter);
                g2.setColor(new Color(32, 32, 32));
                drawFittedLabel(g2, shortPackageName(metric.packageName()), labelX, labelY, right - labelX);
            }

            g2.dispose();
        }

        private void drawAxisTicks(Graphics2D g2, int left, int top, int right, int bottom) {
            FontMetrics fontMetrics = g2.getFontMetrics();
            for (int i = 0; i <= 4; i++) {
                double value = i / 4.0;
                int x = left + scale(value, right - left);
                int y = bottom - scale(value, bottom - top);
                String label = String.format("%.2f", value);

                g2.drawLine(x, bottom - 4, x, bottom + 4);
                g2.drawString(label, x - fontMetrics.stringWidth(label) / 2, bottom + 20);
                g2.drawLine(left - 4, y, left + 4, y);
                g2.drawString(label, left - fontMetrics.stringWidth(label) - 8, y + 5);
            }
        }

        private void drawLegend(Graphics2D g2, int x, int y) {
            drawLegendItem(g2, x, y, MetricLevel.LOW.color(), "low D");
            drawLegendItem(g2, x + 76, y, MetricLevel.MEDIUM.color(), "mid D");
            drawLegendItem(g2, x + 152, y, MetricLevel.HIGH.color(), "high D");
        }

        private void drawLegendItem(Graphics2D g2, int x, int y, Color color, String label) {
            g2.setColor(color);
            g2.fillOval(x, y - 10, 10, 10);
            g2.setColor(new Color(32, 32, 32));
            g2.drawString(label, x + 16, y);
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

        private String shortPackageName(String packageName) {
            if ("(default)".equals(packageName)) {
                return packageName;
            }
            int lastDot = packageName.lastIndexOf('.');
            if (lastDot < 0 || lastDot == packageName.length() - 1) {
                return packageName;
            }
            return packageName.substring(lastDot + 1);
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
