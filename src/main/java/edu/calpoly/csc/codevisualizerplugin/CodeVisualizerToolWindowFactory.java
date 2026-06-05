package edu.calpoly.csc.codevisualizerplugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class CodeVisualizerToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        CodeVisualizerToolWindow view = new CodeVisualizerToolWindow(project);
        Content content = ContentFactory.getInstance().createContent(view.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
