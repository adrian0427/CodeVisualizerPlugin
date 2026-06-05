package edu.calpoly.csc.codevisualizerplugin.analysis;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public record JavaFileMetric(
        String fileName,
        String packageName,
        String relativePath,
        VirtualFile virtualFile,
        List<String> classNames,
        int classCount,
        int methodCount,
        int fieldCount,
        int branchCount
) {
    public JavaFileMetric {
        classNames = List.copyOf(classNames);
    }

    public int score() {
        return classCount + methodCount + fieldCount + branchCount;
    }

    public MetricLevel level() {
        return MetricLevel.fromScore(score());
    }
}
