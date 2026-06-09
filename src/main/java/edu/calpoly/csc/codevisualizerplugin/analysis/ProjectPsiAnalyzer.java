package edu.calpoly.csc.codevisualizerplugin.analysis;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProjectPsiAnalyzer {
    public ProjectAnalysisResult analyze(Project project) {
        List<VirtualFile> javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))
                .stream()
                .filter(file -> !file.isDirectory())
                .sorted(Comparator.comparing(VirtualFile::getPath))
                .toList();

        PsiManager psiManager = PsiManager.getInstance(project);
        List<JavaFileMetric> metrics = new ArrayList<>();
        List<JavaClassInfo> classes = new ArrayList<>();
        Set<JavaRelationship> relationships = new LinkedHashSet<>();

        for (VirtualFile javaFile : javaFiles) {
            PsiElement psiFile = psiManager.findFile(javaFile);
            if (psiFile instanceof PsiJavaFile psiJavaFile) {
                FileAnalysis fileAnalysis = analyzeFile(project, psiJavaFile, javaFile);
                metrics.add(fileAnalysis.metric());
                classes.addAll(fileAnalysis.classes());
                relationships.addAll(fileAnalysis.relationships());
            }
        }

        Set<String> projectClassNames = classes.stream()
                .map(JavaClassInfo::qualifiedName)
                .collect(Collectors.toSet());
        List<JavaRelationship> projectRelationships = relationships.stream()
                .filter(relationship -> projectClassNames.contains(relationship.sourceQualifiedName()))
                .filter(relationship -> projectClassNames.contains(relationship.targetQualifiedName()))
                .filter(relationship -> !relationship.sourceQualifiedName().equals(relationship.targetQualifiedName()))
                .toList();

        return new ProjectAnalysisResult(
                project.getName(),
                metrics,
                classes,
                projectRelationships,
                createPackageMetrics(classes, projectRelationships),
                createPlantUml(project.getName(), classes, projectRelationships)
        );
    }

    private FileAnalysis analyzeFile(Project project, PsiJavaFile psiJavaFile, VirtualFile virtualFile) {
        FileMetricVisitor visitor = new FileMetricVisitor();
        psiJavaFile.accept(visitor);

        JavaFileMetric metric = new JavaFileMetric(
                virtualFile.getName(),
                psiJavaFile.getPackageName(),
                relativePath(project, virtualFile),
                virtualFile,
                visitor.classes(),
                visitor.classes().size(),
                visitor.methodCount(),
                visitor.constructorCount(),
                visitor.fieldCount(),
                visitor.branchCount(),
                countLinesOfCode(psiJavaFile)
        );

        return new FileAnalysis(metric, visitor.classes(), visitor.relationships());
    }

    private List<PackageMetric> createPackageMetrics(List<JavaClassInfo> classes, List<JavaRelationship> relationships) {
        Map<String, List<JavaClassInfo>> classesByPackage = classes.stream()
                .collect(Collectors.groupingBy(
                        JavaClassInfo::packageName,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));

        return classesByPackage.entrySet().stream()
                .map(entry -> createPackageMetric(entry.getKey(), entry.getValue(), relationships, classes))
                .sorted(Comparator.comparing(PackageMetric::packageName))
                .toList();
    }

    private PackageMetric createPackageMetric(
            String packageName,
            List<JavaClassInfo> packageClasses,
            List<JavaRelationship> relationships,
            List<JavaClassInfo> allClasses
    ) {
        Map<String, String> classPackages = allClasses.stream()
                .collect(Collectors.toMap(
                        JavaClassInfo::qualifiedName,
                        JavaClassInfo::packageName,
                        (left, right) -> left
                ));
        Set<String> packageClassNames = packageClasses.stream()
                .map(JavaClassInfo::qualifiedName)
                .collect(Collectors.toSet());

        int incomingDependencies = 0;
        int outgoingDependencies = 0;

        for (JavaRelationship relationship : relationships) {
            boolean sourceInPackage = packageClassNames.contains(relationship.sourceQualifiedName());
            boolean targetInPackage = packageClassNames.contains(relationship.targetQualifiedName());
            if (sourceInPackage == targetInPackage) {
                continue;
            }
            if (sourceInPackage) {
                String targetPackage = classPackages.get(relationship.targetQualifiedName());
                if (!packageName.equals(targetPackage)) {
                    outgoingDependencies++;
                }
            } else if (targetInPackage) {
                String sourcePackage = classPackages.get(relationship.sourceQualifiedName());
                if (!packageName.equals(sourcePackage)) {
                    incomingDependencies++;
                }
            }
        }

        int abstractClassCount = (int) packageClasses.stream()
                .filter(this::isAbstractDesignElement)
                .count();
        double abstractness = packageClasses.isEmpty() ? 0.0 : abstractClassCount / (double) packageClasses.size();
        double instabilityDenominator = incomingDependencies + outgoingDependencies;
        double instability = instabilityDenominator == 0.0 ? 0.0 : outgoingDependencies / instabilityDenominator;
        double distanceFromMainSequence = Math.abs(abstractness + instability - 1.0);

        return new PackageMetric(
                packageName.isBlank() ? "(default)" : packageName,
                packageClasses.size(),
                abstractClassCount,
                incomingDependencies,
                outgoingDependencies,
                abstractness,
                instability,
                distanceFromMainSequence
        );
    }

    private boolean isAbstractDesignElement(JavaClassInfo classInfo) {
        return classInfo.kind() == ClassKind.ABSTRACT_CLASS || classInfo.kind() == ClassKind.INTERFACE;
    }

    private int countLinesOfCode(PsiJavaFile psiJavaFile) {
        String text = psiJavaFile.getText();
        int linesOfCode = 0;
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                linesOfCode++;
            }
        }
        return linesOfCode;
    }

    private String createPlantUml(String projectName, List<JavaClassInfo> classes, List<JavaRelationship> relationships) {
        StringBuilder builder = new StringBuilder();
        builder.append("@startuml\n");
        builder.append("!pragma layout smetana\n");
        builder.append("title ").append(projectName).append(" Java Classes\n");
        builder.append("skinparam classAttributeIconSize 0\n");
        builder.append("hide empty members\n");

        Map<String, List<JavaClassInfo>> classesByPackage = classes.stream()
                .sorted(Comparator.comparing(JavaClassInfo::qualifiedName))
                .collect(Collectors.groupingBy(
                        JavaClassInfo::packageName,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (Map.Entry<String, List<JavaClassInfo>> entry : classesByPackage.entrySet()) {
            String packageName = entry.getKey().isBlank() ? "(default)" : entry.getKey();
            builder.append("package \"").append(escapePlantUml(packageName)).append("\" {\n");
            for (JavaClassInfo classInfo : entry.getValue()) {
                builder.append("  ")
                        .append(classInfo.plantUmlKeyword())
                        .append(" \"")
                        .append(escapePlantUml(classInfo.simpleName()))
                        .append("\" as ")
                        .append(plantUmlIdentifier(classInfo.qualifiedName()))
                        .append(" {\n")
                        .append("    methods: ")
                        .append(classInfo.methodCount())
                        .append("\n")
                        .append("    fields: ")
                        .append(classInfo.fieldCount())
                        .append("\n")
                        .append("  }\n");
            }
            builder.append("}\n");
        }

        for (JavaRelationship relationship : relationships) {
            builder.append(plantUmlIdentifier(relationship.sourceQualifiedName()))
                    .append(" ")
                    .append(relationship.type().plantUmlArrow())
                    .append(" ")
                    .append(plantUmlIdentifier(relationship.targetQualifiedName()))
                    .append("\n");
        }

        builder.append("@enduml\n");
        return builder.toString();
    }

    private String relativePath(Project project, VirtualFile virtualFile) {
        String basePath = project.getBasePath();
        String filePath = virtualFile.getPath();
        if (basePath != null && filePath.startsWith(basePath)) {
            return filePath.substring(basePath.length()).replaceFirst("^/", "");
        }
        return filePath;
    }

    private String escapePlantUml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String plantUmlIdentifier(String className) {
        return className.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static final class FileMetricVisitor extends JavaRecursiveElementVisitor {
        private final List<JavaClassInfo> classes = new ArrayList<>();
        private final Set<JavaRelationship> relationships = new LinkedHashSet<>();
        private int methodCount;
        private int constructorCount;
        private int fieldCount;
        private int branchCount;

        @Override
        public void visitClass(PsiClass psiClass) {
            String qualifiedName = psiClass.getQualifiedName();
            String simpleName = psiClass.getName();
            if (qualifiedName != null && simpleName != null) {
                JavaClassInfo classInfo = createClassInfo(psiClass, qualifiedName, simpleName);
                classes.add(classInfo);
                methodCount += classInfo.methodCount();
                constructorCount += classInfo.constructorCount();
                fieldCount += classInfo.fieldCount();
                collectRelationships(psiClass, qualifiedName);
            }
            super.visitClass(psiClass);
        }

        private JavaClassInfo createClassInfo(PsiClass psiClass, String qualifiedName, String simpleName) {
            int methods = 0;
            int constructors = 0;
            for (PsiMethod method : psiClass.getMethods()) {
                if (method.isConstructor()) {
                    constructors++;
                } else {
                    methods++;
                }
            }

            return new JavaClassInfo(
                    qualifiedName,
                    simpleName,
                    packageName(qualifiedName, simpleName),
                    classKind(psiClass),
                    methods,
                    constructors,
                    psiClass.getFields().length
            );
        }

        private ClassKind classKind(PsiClass psiClass) {
            if (psiClass.isAnnotationType()) {
                return ClassKind.ANNOTATION;
            }
            if (psiClass.isInterface()) {
                return ClassKind.INTERFACE;
            }
            if (psiClass.isEnum()) {
                return ClassKind.ENUM;
            }
            if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return ClassKind.ABSTRACT_CLASS;
            }
            return ClassKind.CLASS;
        }

        private void collectRelationships(PsiClass psiClass, String qualifiedName) {
            PsiClass superClass = psiClass.getSuperClass();
            if (superClass != null) {
                addRelationship(qualifiedName, superClass, RelationshipType.EXTENDS);
            }

            for (PsiClass psiInterface : psiClass.getInterfaces()) {
                addRelationship(qualifiedName, psiInterface, RelationshipType.IMPLEMENTS);
            }

            for (PsiField field : psiClass.getFields()) {
                addTypeRelationship(qualifiedName, field.getType());
            }

            for (PsiMethod method : psiClass.getMethods()) {
                addTypeRelationship(qualifiedName, method.getReturnType());
                for (PsiParameter parameter : method.getParameterList().getParameters()) {
                    addTypeRelationship(qualifiedName, parameter.getType());
                }
            }
        }

        private void addRelationship(String sourceQualifiedName, PsiClass targetClass, RelationshipType type) {
            String targetQualifiedName = targetClass.getQualifiedName();
            if (targetQualifiedName != null && !"java.lang.Object".equals(targetQualifiedName)) {
                relationships.add(new JavaRelationship(sourceQualifiedName, targetQualifiedName, type));
            }
        }

        private void addTypeRelationship(String sourceQualifiedName, PsiType type) {
            if (type instanceof PsiClassType classType) {
                PsiClass targetClass = classType.resolve();
                if (targetClass != null) {
                    addRelationship(sourceQualifiedName, targetClass, RelationshipType.USES);
                }
            }
        }

        private String packageName(String qualifiedName, String simpleName) {
            int packageLength = qualifiedName.length() - simpleName.length() - 1;
            if (packageLength <= 0) {
                return "";
            }
            return qualifiedName.substring(0, packageLength);
        }

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            branchCount++;
            super.visitIfStatement(statement);
        }

        @Override
        public void visitForStatement(PsiForStatement statement) {
            branchCount++;
            super.visitForStatement(statement);
        }

        @Override
        public void visitForeachStatement(PsiForeachStatement statement) {
            branchCount++;
            super.visitForeachStatement(statement);
        }

        @Override
        public void visitWhileStatement(PsiWhileStatement statement) {
            branchCount++;
            super.visitWhileStatement(statement);
        }

        @Override
        public void visitConditionalExpression(PsiConditionalExpression expression) {
            branchCount++;
            super.visitConditionalExpression(expression);
        }

        @Override
        public void visitSwitchStatement(PsiSwitchStatement statement) {
            branchCount++;
            super.visitSwitchStatement(statement);
        }

        List<JavaClassInfo> classes() {
            return classes;
        }

        int methodCount() {
            return methodCount;
        }

        int constructorCount() {
            return constructorCount;
        }

        int fieldCount() {
            return fieldCount;
        }

        int branchCount() {
            return branchCount;
        }

        Set<JavaRelationship> relationships() {
            return relationships;
        }
    }

    private record FileAnalysis(
            JavaFileMetric metric,
            List<JavaClassInfo> classes,
            Set<JavaRelationship> relationships
    ) {
    }
}
