package edu.calpoly.csc.codevisualizerplugin.analysis;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public record JavaFileMetric(
        String fileName,
        String packageName,
        String relativePath,
        VirtualFile virtualFile,
        List<JavaClassInfo> classes,
        int classCount,
        int methodCount,
        int constructorCount,
        int fieldCount,
        int branchCount,
        int linesOfCode
) {
    public JavaFileMetric {
        classes = List.copyOf(classes);
    }

    public int score() {
        return classCount + methodCount + constructorCount + fieldCount + branchCount + roughCyclomaticComplexity();
    }

    public int roughCyclomaticComplexity() {
        return branchCount + 1;
    }

    public MetricLevel level() {
        return MetricLevel.fromScore(score());
    }
}
