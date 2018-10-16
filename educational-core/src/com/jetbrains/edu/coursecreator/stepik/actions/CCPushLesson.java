package com.jetbrains.edu.coursecreator.stepik.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task.Modal;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.stepik.CCStepikConnector;
import com.jetbrains.edu.learning.EduUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.courseFormat.ext.CourseExt;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.stepik.StepikConnector;
import com.jetbrains.edu.learning.stepik.StepikNames;
import com.jetbrains.edu.learning.stepik.StepikWrappers;
import com.jetbrains.edu.learning.stepik.courseFormat.StepikChangeStatus;
import com.jetbrains.edu.learning.stepik.courseFormat.StepikCourse;
import com.jetbrains.edu.learning.stepik.courseFormat.ext.StepikLessonExt;
import com.jetbrains.edu.learning.stepik.courseFormat.ext.StepikSectionExt;
import com.jetbrains.edu.learning.stepik.courseFormat.remoteInfo.StepikCourseRemoteInfo;
import com.jetbrains.edu.learning.stepik.courseFormat.remoteInfo.StepikRemoteInfo;
import com.twelvemonkeys.lang.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.jetbrains.edu.learning.stepik.courseFormat.ext.StepikLessonExt.isStepikLesson;

@SuppressWarnings("ComponentNotRegistered") // educational-core.xml
public class CCPushLesson extends DumbAwareAction {
  public CCPushLesson() {
    super("Update Lesson on Stepik", "Update Lesson on Stepik", null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(false);
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (view == null || project == null) {
      return;
    }
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (!(course instanceof StepikCourse)) {
      return;
    }
    if (!course.getCourseMode().equals(CCUtils.COURSE_MODE)) return;
    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0 || directories.length > 1) {
      return;
    }

    final PsiDirectory lessonDir = directories[0];
    if (lessonDir == null) {
      return;
    }

    Lesson lesson = EduUtils.getLesson(lessonDir.getVirtualFile(), course);
    if (lesson == null) {
      return;
    }

    final Section section = lesson.getSection();
    if (section == null || section.getRemoteInfo() instanceof StepikRemoteInfo) {
      e.getPresentation().setEnabledAndVisible(true);
      if (!isStepikLesson(lesson)) {
        e.getPresentation().setText("Upload Lesson to Stepik");
      }
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (view == null || project == null) {
      return;
    }
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (!(course instanceof StepikCourse)) {
      return;
    }
    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0 || directories.length > 1) {
      return;
    }

    final PsiDirectory lessonDir = directories[0];
    if (lessonDir == null || !lessonDir.getName().contains("lesson")) {
      return;
    }

    Lesson lesson = EduUtils.getLesson(lessonDir.getVirtualFile(), course);
    if (lesson == null) {
      return;
    }

    if (CourseExt.getHasSections(course) && lesson.getSection() == null && !(isStepikLesson(lesson))) {
      wrapAndPost(project, course, lesson);
      return;
    }

    ProgressManager.getInstance().run(new Modal(project, "Uploading Lesson", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText("Uploading lesson to " + StepikNames.STEPIK_URL);
        doPush(lesson, project, (StepikCourse)course);
      }
    });
  }

  private static void wrapAndPost(Project project, Course course, Lesson lesson) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      int result = Messages.showYesNoDialog(project, "Since you have sections, we'll have to wrap this lesson into section before upload",
                                            "Wrap Lesson Into Sections", "Wrap and Post", "Cancel", null);
      if (result == Messages.YES) {
       Section section = CCUtils.wrapIntoSection(project, course, Collections.singletonList(lesson), sectionToWrapIntoName(lesson));
        if (section != null) {
          CCPushSection.doPush(project, section, (StepikCourse)course);
        }
      }
    });
  }

  @NotNull
  private static String sectionToWrapIntoName(Lesson lesson) {
    return "Section. " + StringUtil.capitalize(lesson.getName());
  }

  // public for tests
  public static void doPush(Lesson lesson, Project project, StepikCourse course) {
    if (isStepikLesson(lesson)) {
      StepikWrappers.Unit unit = StepikConnector.getUnit(StepikLessonExt.getUnitId(lesson));

      int sectionId;
      if (lesson.getSection() != null) {
        sectionId = StepikSectionExt.getId(lesson.getSection());
      }
      else {
        sectionId = CCStepikConnector.getTopLevelSectionId(project, course);
      }
      Lesson updatedLesson = CCStepikConnector.updateLesson(project, lesson, true, sectionId);
      int lessonId = updatedLesson == null ? -1 : StepikLessonExt.getId(updatedLesson);
      lesson.setStepikChangeStatus(StepikChangeStatus.UP_TO_DATE);
      setUpdated(lesson);
      if (lessonId != -1) {
        boolean positionChanged = lesson.getIndex() != unit.getPosition();
        if (positionChanged) {
          List<Lesson> lessons = lesson.getSection() != null ? lesson.getSection().getLessons() : course.getLessons();
          updateLessonsPositions(project, 0, lessons);
        }
        CCStepikConnector.showNotification(project, "Lesson updated", CCStepikConnector.openOnStepikAction("/lesson/" + lessonId));
      }
    }
    else {
      if (CourseExt.getHasSections(course)) {
        Section section = lesson.getSection();
        assert section != null;
        int position = lessonPosition(section, lesson);
        CCStepikConnector.postLesson(project, lesson, lesson.getIndex(), StepikSectionExt.getId(section));
        if (lesson.getIndex() < section.getLessons().size()) {
          updateLessonsPositions(project, position + 1, section.getLessons());
        }
      }
      else {
        int position = lessonPosition(course, lesson);
        int sectionId;
        final StepikCourseRemoteInfo remoteInfo = course.getStepikRemoteInfo();
        final List<Integer> sections = remoteInfo.getSectionIds();
        sectionId = sections.get(sections.size() - 1);
        CCStepikConnector.postLesson(project, lesson, lesson.getIndex(), sectionId);
        if (lesson.getIndex() < course.getLessons().size()) {
          List<Lesson> lessons = course.getLessons();
          updateLessonsPositions(project, position + 1, lessons);
        }
      }
      CCStepikConnector.showNotification(project, "Lesson uploaded",
                                         CCStepikConnector.openOnStepikAction("/lesson/" + StepikLessonExt.getId(lesson)));
    }
  }

  private static void setUpdated(Lesson lesson) {
    for (Task task : lesson.taskList) {
      task.setStepikChangeStatus(StepikChangeStatus.UP_TO_DATE);
    }
  }

  private static void updateLessonsPositions(@NotNull Project project, int initialPosition, List<Lesson> lessons) {
    int position = 1;

    // update positions for posted lessons only
    for (Lesson lesson : lessons) {
      // skip unpushed lessons
      if (!(isStepikLesson(lesson))) {
        continue;
      }

      // skip lessons before target
      if (position < initialPosition) {
        continue;
      }
      int index = lesson.getIndex();
      lesson.setIndex(position++);
      int sectionId;
      if (lesson.getSection() != null) {
        sectionId = StepikSectionExt.getId(lesson.getSection());
      }
      else {
        StepikCourse course = (StepikCourse)StudyTaskManager.getInstance(project).getCourse();
        assert  course != null;
        sectionId = CCStepikConnector.getTopLevelSectionId(project, course);
      }
      CCStepikConnector.updateLessonInfo(project, lesson, false, sectionId);
      lesson.setStepikChangeStatus(StepikChangeStatus.UP_TO_DATE);
      lesson.setIndex(index);
    }
  }

  private static int lessonPosition(@NotNull ItemContainer parent, @NotNull Lesson lesson) {
    int position = 1;
    for (StudyItem item : parent.getItems()) {
      if (!isStepikLesson(lesson)) {
        continue;
      }

      if (item.getName().equals(lesson.getName())) {
        continue;
      }

      position ++;
    }

    return position;
  }
}