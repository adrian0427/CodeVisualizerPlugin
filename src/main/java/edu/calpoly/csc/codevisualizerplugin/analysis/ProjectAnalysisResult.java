package edu.calpoly.csc.codevisualizerplugin.analysis;

import java.util.Comparator;
import java.util.List;

public record ProjectAnalysisResult(
        String projectName,
        List<JavaFileMetric> files,
        List<JavaClassInfo> classes,
        List<JavaRelationship> relationships,
        List<PackageMetric> packages,
        String plantUml
) {
    public ProjectAnalysisResult {
        files = files.stream()
                .sorted(Comparator.comparing(JavaFileMetric::relativePath))
                .toList();
        classes = List.copyOf(classes);
        relationships = List.copyOf(relationships);
        packages = List.copyOf(packages);
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

    public int totalRelationshipCount() {
        return relationships.size();
    }

    public int totalLinesOfCode() {
        return files.stream().mapToInt(JavaFileMetric::linesOfCode).sum();
    }
}
