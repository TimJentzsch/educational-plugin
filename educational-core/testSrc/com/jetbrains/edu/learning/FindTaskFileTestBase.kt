package com.jetbrains.edu.learning

import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.TaskFile
import com.jetbrains.edu.learning.courseFormat.tasks.Task

abstract class FindTaskFileTestBase<Settings> : CourseGenerationTestBase<Settings>() {

  protected fun doTestGetTaskDir(pathToCourseJson: String, filePath: String, taskDirPath: String) {
    val course = generateCourseStructure(pathToCourseJson)

    val file = findFile(filePath)
    val expectedTaskDir = findFile(taskDirPath)
    assertEquals(expectedTaskDir, EduUtils.getTaskDir(course, project, file))
  }

  protected fun doTestGetTaskForFile(pathToCourseJson: String, filePath: String, expectedTask: (Course) -> Task) {
    val course = generateCourseStructure(pathToCourseJson)
    val file = findFile(filePath)
    val task = expectedTask(course)
    assertEquals(task, EduUtils.getTaskForFile(project, file))
  }

  protected fun doTestGetTaskFile(pathToCourseJson: String, filePath: String, expectedTaskFile: (Course) -> TaskFile) {
    val course = generateCourseStructure(pathToCourseJson)
    val file = findFile(filePath)
    val taskFile = expectedTaskFile(course)
    assertEquals(taskFile, EduUtils.getTaskFile(project, file))
  }
}
