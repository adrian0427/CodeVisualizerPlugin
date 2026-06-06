package edu.calpoly.csc.codevisualizerplugin.analysis;

public enum RelationshipType {
    EXTENDS("--|>"),
    IMPLEMENTS("..|>"),
    USES("..>");

    private final String plantUmlArrow;

    RelationshipType(String plantUmlArrow) {
        this.plantUmlArrow = plantUmlArrow;
    }

    public String plantUmlArrow() {
        return plantUmlArrow;
    }
}
