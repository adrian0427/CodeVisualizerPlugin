package edu.calpoly.csc.codevisualizerplugin.analysis;

import java.util.Comparator;
import java.util.List;

public record ProjectAnalysisResult(String projectName, List<JavaFileMetric> files, String plantUml) {
    public ProjectAnalysisResult {
        files = files.stream()
                .sorted(Comparator.comparing(JavaFileMetric::relativePath))
                .toList();
    }

    public int javaFileCount() {
        return files.size();
    }

    public int totalClassCount() {
        return files.stream().mapToInt(JavaFileMetric::classCount).sum();
    }

    public int totalMethodCount() {
        return files.stream().mapToInt(JavaFileMetric::methodCount).sum();
    }
}
