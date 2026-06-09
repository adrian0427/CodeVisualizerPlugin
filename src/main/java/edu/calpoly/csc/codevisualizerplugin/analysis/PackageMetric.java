package edu.calpoly.csc.codevisualizerplugin.analysis;

public record PackageMetric(
        String packageName,
        int classCount,
        int abstractClassCount,
        int incomingDependencies,
        int outgoingDependencies,
        double abstractness,
        double instability,
        double distanceFromMainSequence
) {
}
