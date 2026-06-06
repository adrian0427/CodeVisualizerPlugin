package edu.calpoly.csc.codevisualizerplugin.analysis;

public record JavaRelationship(String sourceQualifiedName, String targetQualifiedName, RelationshipType type) {
}
