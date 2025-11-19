package com.github.cvzakharchenko.extramacros.macros

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

class ReadFromFileMacroTest : BasePlatformTestCase() {

    fun testStripsCommentsAndNormalizesWhitespace() {
        val tempFile = Files.createTempFile("readFromFileMacro", ".txt")
        try {
            Files.writeString(
                tempFile,
                """
                # before comment
                keep   this
                   // another comment
                next	line	with	  tabs


                last line
                """.trimIndent()
            )

            val result = ReadFromFileMacro().expand(emptyDataContext(), tempFile.toString())

            assertEquals("keep this next line with tabs last line", result)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    fun testReturnsNullWhenFileMissing() {
        val result = ReadFromFileMacro().expand(emptyDataContext(), "non-existent-file.txt")
        assertNull(result)
    }

    fun testResolvesRelativePathAgainstProjectRoot() {
        val projectBasePath = project.basePath ?: error("Project base path is null")
        val projectBase = Path.of(projectBasePath)
        val subDir = projectBase.resolve("macroRelative")
        Files.createDirectories(subDir)
        val relativeFile = subDir.resolve("relative.txt")
        Files.writeString(relativeFile, "single line")

        val relativePath = projectBase.relativize(relativeFile).toString()
        val result = ReadFromFileMacro().expand(projectDataContext(), relativePath)

        assertEquals("single line", result)
    }

    private fun emptyDataContext(): DataContext = DataContext { null }

    private fun projectDataContext(): DataContext =
        SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project)
}

