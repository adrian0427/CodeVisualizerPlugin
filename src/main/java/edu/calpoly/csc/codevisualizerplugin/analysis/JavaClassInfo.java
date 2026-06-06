package edu.calpoly.csc.codevisualizerplugin.analysis;

public record JavaClassInfo(
        String qualifiedName,
        String simpleName,
        String packageName,
        ClassKind kind,
        int methodCount,
        int constructorCount,
        int fieldCount
) {
    public String plantUmlKeyword() {
        return switch (kind) {
            case INTERFACE -> "interface";
            case ENUM -> "enum";
            case ANNOTATION -> "annotation";
            case ABSTRACT_CLASS -> "abstract class";
            case CLASS -> "class";
        };
    }
}
