/* Copyright (C) 2021 Julian Valentin, lsp-cli Development Community
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package org.bsplines.lspcli.client

import org.bsplines.lspcli.tools.FileIo
import org.bsplines.lspcli.tools.I18n
import org.bsplines.lspcli.tools.Logging
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.Color
import org.fusesource.jansi.AnsiConsole
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.math.ceil

class Checker(
  val languageClient: LspCliLanguageClient,
  val hideCommands: Boolean = false,
) {
  fun check(paths: List<Path>): Int {
    var numberOfMatches = 0
    for (path: Path in paths) numberOfMatches += check(path)
    return numberOfMatches
  }

  fun check(path: Path): Int {
    val text: String
    val codeLanguageId: String

    if (path.toString() == "-") {
      val outputStream = ByteArrayOutputStream()
      val buffer = ByteArray(STANDARD_INPUT_BUFFER_SIZE)

      while (true) {
        val length = System.`in`.read(buffer)
        if (length == -1) break
        outputStream.write(buffer, 0, length)
      }

      text = outputStream.toString(StandardCharsets.UTF_8)
      codeLanguageId = "plaintext"
    } else if (path.toFile().isDirectory) {
      return checkDirectory(path)
    } else {
      text = FileIo.readFileWithException(path)
      codeLanguageId = FileIo.getCodeLanguageIdFromPath(path) ?: "plaintext"
    }

    return checkFile(path, codeLanguageId, text)
  }

  private fun checkDirectory(path: Path): Int {
    var numberOfMatches = 0

    for (childPath: Path in Files.walk(path).collect(Collectors.toList())) {
      if (childPath.toFile().isFile) {
        val curCodeLanguageId: String? = FileIo.getCodeLanguageIdFromPath(childPath)
        if (curCodeLanguageId != null) numberOfMatches += check(childPath)
      }
    }

    return numberOfMatches
  }

  private fun checkFile(path: Path, languageId: String, text: String): Int {
    val uri: String = path.toUri().toString()
    val document = LspCliTextDocumentItem(uri, languageId, 1, text)
    Logging.logger.info(I18n.format("checkingFile", path.toString()))

    this.languageClient.languageServer.textDocumentService.didOpen(
      DidOpenTextDocumentParams(document)
    )

    Logging.logger.info(I18n.format("waitingForDiagnosticsForFile", path.toString()))
    val diagnostics: List<Diagnostic>

    while (true) {
      val curDiagnostics: List<Diagnostic>? = this.languageClient.diagnosticsMap[uri]

      if (curDiagnostics != null) {
        diagnostics = curDiagnostics
        break
      }

      Thread.sleep(WAIT_FOR_DIAGNOSTIC_MILLISECONDS)
    }

    val documentId = TextDocumentIdentifier(uri)
    val terminalWidth: Int = run {
      val terminalWidth: Int = AnsiConsole.getTerminalWidth()
      if (terminalWidth >= 2) terminalWidth else Integer.MAX_VALUE
    }

    for (diagnostic: Diagnostic in diagnostics) {
      val codeActionTitles = ArrayList<String>()
      val codeActionResult: List<Either<Command, CodeAction>> =
          this.languageClient.languageServer.textDocumentService.codeAction(
            CodeActionParams(documentId, diagnostic.range, CodeActionContext(listOf(diagnostic)))
          ).get()

      for (entry: Either<Command, CodeAction> in codeActionResult) {
        val command: Command? = entry.left
        val codeAction: CodeAction? = entry.right

        if ((command != null) && !this.hideCommands) {
          codeActionTitles.add(command.title)
        } else if ((codeAction != null) && ((codeAction.command == null) || !this.hideCommands)) {
          codeActionTitles.add(codeAction.title)
        }
      }

      printDiagnostic(path, document, diagnostic, codeActionTitles, terminalWidth)
    }

    return diagnostics.size
  }

  companion object {
    private val TRAILING_WHITESPACE_REGEX = Regex("[ \t\r\n]+$")

    private const val STANDARD_INPUT_BUFFER_SIZE = 1024
    private const val WAIT_FOR_DIAGNOSTIC_MILLISECONDS = 50L
    private const val TAB_SIZE = 8

    @Suppress("ComplexMethod")
    private fun printDiagnostic(
      path: Path,
      document: LspCliTextDocumentItem,
      diagnostic: Diagnostic,
      codeActionTitles: List<String>,
      terminalWidth: Int,
    ) {
      val text: String = document.text
      val fromPosition: Position = diagnostic.range.start
      val toPosition: Position = diagnostic.range.end
      val fromPos: Int = document.convertPosition(fromPosition)
      val toPos: Int = document.convertPosition(toPosition)

      val color: Color = when (diagnostic.severity) {
        DiagnosticSeverity.Error -> Color.RED
        DiagnosticSeverity.Warning -> Color.YELLOW
        DiagnosticSeverity.Information -> Color.BLUE
        DiagnosticSeverity.Hint -> Color.BLUE
        else -> Color.BLUE
      }
      val typeString: String = when (diagnostic.severity) {
        DiagnosticSeverity.Error -> "error"
        DiagnosticSeverity.Warning -> "warning"
        DiagnosticSeverity.Information -> "info"
        DiagnosticSeverity.Hint -> "hint"
        else -> "info"
      }

      val diagnosticCode: String = diagnostic.code?.get()?.toString() ?: ""
      val ansi: Ansi = (Ansi.ansi().bold().a(path.toString()).a(":")
          .a(fromPosition.line + 1).a(":").a(fromPosition.character + 1).a(": ")
          .fg(color).a(typeString).a(":").reset().bold().a(" ").a(diagnostic.message))
      if (diagnosticCode.isNotEmpty()) ansi.a(" [").a(diagnosticCode).a("]")
      ansi.reset()
      println(ansi)

      val lineStartPos: Int = document.convertPosition(Position(fromPosition.line, 0))
      val lineEndPos: Int = document.convertPosition(Position(fromPosition.line + 1, 0))
      val line: String = text.substring(lineStartPos, lineEndPos)

      println(Ansi.ansi().a(line.substring(0, fromPos - lineStartPos)).bold().fg(color)
          .a(line.substring(fromPos - lineStartPos, toPos - lineStartPos)).reset()
          .a(line.substring(toPos - lineStartPos).replaceFirst(TRAILING_WHITESPACE_REGEX, "")))

      var indentationSize = guessIndentationSize(text, lineStartPos, fromPos, terminalWidth)

      for (codeActionTitle: String in codeActionTitles) {
        if (indentationSize + codeActionTitle.length > terminalWidth) indentationSize = 0
      }

      val indentation: String = " ".repeat(indentationSize)

      for (codeActionTitle: String in codeActionTitles) {
        println(Ansi.ansi().a(indentation).fg(Color.GREEN).a(codeActionTitle).reset())
      }
    }

    private fun guessIndentationSize(
      text: String,
      lineStartPos: Int,
      fromPos: Int,
      terminalWidth: Int,
    ): Int {
      var indentationSize = 0

      for (pos in lineStartPos until fromPos) {
        if (text[pos] == '\t') {
          indentationSize = (ceil((indentationSize + 1.0) / TAB_SIZE) * TAB_SIZE).toInt()
        } else {
          indentationSize++
        }

        if (indentationSize >= terminalWidth) indentationSize = 0
      }

      return indentationSize
    }
  }
}
