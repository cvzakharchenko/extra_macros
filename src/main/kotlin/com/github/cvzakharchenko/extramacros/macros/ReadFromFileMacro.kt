package com.github.cvzakharchenko.extramacros.macros

import com.github.cvzakharchenko.extramacros.MyBundle
import com.intellij.ide.macro.Macro
import com.intellij.ide.macro.MacroWithParams
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

open class ReadFromFileMacro : Macro(), MacroWithParams {

    override fun getName() = "ReadFromFile"

    override fun getDescription() = MyBundle.message("macro.readFromFile.description")

    override fun expand(dataContext: DataContext): String? = missingPathMessage()

    override fun expand(dataContext: DataContext, vararg args: String?): String? {
        val project = CommonDataKeys.PROJECT.getData(dataContext)
        val rawPath = args.firstOrNull()?.takeUnless { it.isNullOrBlank() } ?: run {
            notifyMissingPath(project)
            return null
        }

        val sanitizedPath = rawPath.trim().trim('"')
        if (sanitizedPath.isEmpty()) {
            notifyMissingPath(project)
            return null
        }

        val resolvedPath = try {
            val candidate = Path.of(sanitizedPath)
            if (candidate.isAbsolute || project?.basePath == null) {
                candidate.normalize()
            } else {
                Path.of(project.basePath!!).resolve(candidate).normalize()
            }
        } catch (exception: InvalidPathException) {
            notifyFailure(
                project,
                MyBundle.message("macro.readFromFile.error.generic", sanitizedPath)
            )
            return null
        }

        val processed = try {
            Files.readAllLines(resolvedPath).asSequence()
        } catch (exception: Exception) {
            notifyFailure(
                project,
                MyBundle.message("macro.readFromFile.error.generic", resolvedPath)
            )
            return null
        }.filterNot(::isCommentLine)
            .joinToString(" ") { it.trim() }

        val normalized = WHITESPACE_REGEX.replace(processed, " ").trim()
        return normalized.ifEmpty { null }
    }

    private fun isCommentLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("#") || trimmed.startsWith("//")
    }

    internal open fun notifyFailure(project: Project?, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                MyBundle.message("macro.readFromFile.notification.title"),
                message,
                NotificationType.ERROR
            )
            .notify(project)
    }

    companion object {
        private val WHITESPACE_REGEX = "\\s+".toRegex()
        private const val NOTIFICATION_GROUP_ID = "com.github.cvzakharchenko.extramacros.notifications"
    }

    private fun notifyMissingPath(project: Project?) {
        notifyFailure(project, missingPathMessage())
    }

    private fun missingPathMessage() = MyBundle.message("macro.readFromFile.error.noArgs")
}

