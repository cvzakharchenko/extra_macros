package com.github.cvzakharchenko.extramacros.macros

import com.github.cvzakharchenko.extramacros.MyBundle
import com.intellij.ide.macro.Macro
import com.intellij.ide.macro.MacroWithParams
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

open class ReadFromFileMacro : Macro(), MacroWithParams {

    override fun getName() = "ReadFromFile"

    override fun getDescription() = MyBundle.message("macro.readFromFile.description")

    override fun expand(dataContext: DataContext): String? {
        val project = CommonDataKeys.PROJECT.getData(dataContext)
        notifyMissingPath(project)
        return null
    }

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

        return try {
            val resolvedPath = resolvePath(sanitizedPath, project)
            normalizeFileContent(resolvedPath, project)
        } catch (exception: InvalidPathException) {
            LOG.warn("Invalid file path supplied to ReadFromFile macro: $sanitizedPath", exception)
            val reason = exception.message
                ?: MyBundle.message("macro.readFromFile.error.reason.invalidPath")
            notifyFailure(
                project,
                MyBundle.message("macro.readFromFile.error.generic", sanitizedPath, reason)
            )
            null
        }
    }

    private fun resolvePath(pathString: String, project: Project?): Path {
        val candidate = Path.of(pathString)

        if (candidate.isAbsolute || project?.basePath == null) {
            return candidate
        }

        return Path.of(project.basePath!!).resolve(candidate).normalize()
    }

    private fun normalizeFileContent(path: Path, project: Project?): String? {
        val processed = try {
            Files.readAllLines(path).asSequence()
        } catch (exception: IOException) {
            LOG.warn("Failed to read file for ReadFromFile macro: $path", exception)
            val reason = exception.message
                ?: MyBundle.message("macro.readFromFile.error.reason.io")
            notifyFailure(
                project,
                MyBundle.message("macro.readFromFile.error.generic", path, reason)
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
        private val LOG = Logger.getInstance(ReadFromFileMacro::class.java)
        private const val NOTIFICATION_GROUP_ID = "com.github.cvzakharchenko.extramacros.notifications"
    }

    private fun notifyMissingPath(project: Project?) {
        notifyFailure(
            project,
            MyBundle.message("macro.readFromFile.error.noArgs")
        )
    }
}

