package com.jetbrains.edu.coursecreator.actions.taskFile;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class CCHideFromStudent extends CCTaskFileActionBase {
  private static final String ACTION_NAME = "Hide from Student";

  public CCHideFromStudent() {
    super(ACTION_NAME);
  }

  @Override
  protected void performAction(VirtualFile file, Task task, Course course, Project project) {
    TaskFile taskFile = EduUtils.getTaskFile(project, file);
    if (taskFile == null) {
      return;
    }
    EduUtils.runUndoableAction(project, ACTION_NAME, new HideTaskFile(project, file, task, taskFile));
  }

  private static class HideTaskFile extends TaskUndoableAction {

    private final Project myProject;
    private final TaskFile myTaskFile;

    public HideTaskFile(Project project, VirtualFile file, Task task, TaskFile taskFile) {
      super(task, file);
      myProject = project;
      myTaskFile = taskFile;
    }

    @Override
    public void performUndo() {
      getTask().getTaskFiles().put(EduUtils.pathRelativeToTask(myProject, getFile()), myTaskFile);
      if (!myTaskFile.getAnswerPlaceholders().isEmpty() && FileEditorManager.getInstance(myProject).isFileOpen(getFile())) {
        for (FileEditor fileEditor : FileEditorManager.getInstance(myProject).getEditors(getFile())) {
          if (fileEditor instanceof TextEditor) {
            Editor editor = ((TextEditor)fileEditor).getEditor();
            EduUtils.drawAllAnswerPlaceholders(editor, myTaskFile);
          }
        }
      }
      ProjectView.getInstance(myProject).refresh();
    }

    @Override
    public void performRedo() {
      hideFromStudent(getFile(), myProject, getTask().getTaskFiles(), myTaskFile);
      ProjectView.getInstance(myProject).refresh();
    }
  }

  public static void hideFromStudent(VirtualFile file, Project project, Map<String, TaskFile> taskFiles, @NotNull final TaskFile taskFile) {
    if (!taskFile.getAnswerPlaceholders().isEmpty() && FileEditorManager.getInstance(project).isFileOpen(file)) {
      for (FileEditor fileEditor : FileEditorManager.getInstance(project).getEditors(file)) {
        if (fileEditor instanceof TextEditor) {
          Editor editor = ((TextEditor)fileEditor).getEditor();
          editor.getMarkupModel().removeAllHighlighters();
        }
      }
    }
    String taskRelativePath = EduUtils.pathRelativeToTask(project, file);
    taskFiles.remove(taskRelativePath);
  }

  @Override
  protected boolean isAvailable(Project project, VirtualFile file) {
    return EduUtils.getTaskFile(project, file) != null;
  }
}