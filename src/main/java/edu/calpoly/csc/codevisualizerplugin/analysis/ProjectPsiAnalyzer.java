package edu.calpoly.csc.codevisualizerplugin.analysis;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ProjectPsiAnalyzer {
    public ProjectAnalysisResult analyze(Project project) {
        List<VirtualFile> javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))
                .stream()
                .filter(file -> !file.isDirectory())
                .sorted(Comparator.comparing(VirtualFile::getPath))
                .toList();

        PsiManager psiManager = PsiManager.getInstance(project);
        List<JavaFileMetric> metrics = new ArrayList<>();

        for (VirtualFile javaFile : javaFiles) {
            PsiElement psiFile = psiManager.findFile(javaFile);
            if (psiFile instanceof PsiJavaFile psiJavaFile) {
                metrics.add(analyzeFile(project, psiJavaFile, javaFile));
            }
        }

        return new ProjectAnalysisResult(project.getName(), metrics, createPlantUml(project.getName(), metrics));
    }

    private JavaFileMetric analyzeFile(Project project, PsiJavaFile psiJavaFile, VirtualFile virtualFile) {
        FileMetricVisitor visitor = new FileMetricVisitor();
        psiJavaFile.accept(visitor);

        return new JavaFileMetric(
                virtualFile.getName(),
                psiJavaFile.getPackageName(),
                relativePath(project, virtualFile),
                virtualFile,
                visitor.classNames(),
                visitor.classCount(),
                visitor.methodCount(),
                visitor.fieldCount(),
                visitor.branchCount()
        );
    }

    private String createPlantUml(String projectName, List<JavaFileMetric> metrics) {
        StringBuilder builder = new StringBuilder();
        builder.append("@startuml\n");
        builder.append("title ").append(projectName).append(" Java Classes\n");
        builder.append("skinparam classAttributeIconSize 0\n");

        for (JavaFileMetric metric : metrics) {
            String packageName = metric.packageName().isBlank() ? "(default)" : metric.packageName();
            builder.append("package \"").append(escapePlantUml(packageName)).append("\" {\n");
            for (String className : metric.classNames()) {
                builder.append("  class ").append(plantUmlIdentifier(className)).append("\n");
            }
            builder.append("}\n");
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
        private final List<String> classNames = new ArrayList<>();
        private int classCount;
        private int methodCount;
        private int fieldCount;
        private int branchCount;

        @Override
        public void visitClass(PsiClass psiClass) {
            classCount++;
            String qualifiedName = psiClass.getQualifiedName();
            classNames.add(qualifiedName == null ? psiClass.getName() : qualifiedName);
            super.visitClass(psiClass);
        }

        @Override
        public void visitMethod(PsiMethod method) {
            methodCount++;
            super.visitMethod(method);
        }

        @Override
        public void visitField(PsiField field) {
            fieldCount++;
            super.visitField(field);
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

        List<String> classNames() {
            return classNames;
        }

        int classCount() {
            return classCount;
        }

        int methodCount() {
            return methodCount;
        }

        int fieldCount() {
            return fieldCount;
        }

        int branchCount() {
            return branchCount;
        }
    }
}
