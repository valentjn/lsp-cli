/* Copyright (C) 2021 Julian Valentin, lsp-cli Development Community
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package org.bsplines.lspcli.client

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentItem

class LspCliTextDocumentItem(
  uri: String,
  codeLanguageId: String,
  version: Int,
  text: String,
) : TextDocumentItem(uri, codeLanguageId, version, text) {
  private val lineStartPosList: MutableList<Int> = ArrayList()

  init {
    reinitializeLineStartPosList(text)
  }

  private fun reinitializeLineStartPosList(text: String) {
    this.lineStartPosList.clear()
    this.lineStartPosList.add(0)

    var i = 0

    while (i < text.length) {
      val c: Char = text[i]

      if (c == '\r') {
        if ((i + 1 < text.length) && (text[i + 1] == '\n')) i++
        this.lineStartPosList.add(i + 1)
      } else if (c == '\n') {
        this.lineStartPosList.add(i + 1)
      }

      i++
    }
  }

  @Suppress("NestedBlockDepth")
  fun convertPosition(position: Position): Int {
    val line: Int = position.line
    val character: Int = position.character
    val text: String = text

    return when {
      line < 0 -> 0
      line >= this.lineStartPosList.size -> text.length
      else -> {
        val lineStart: Int = this.lineStartPosList[line]
        val nextLineStart: Int = if (line < this.lineStartPosList.size - 1) {
          this.lineStartPosList[line + 1]
        } else {
          text.length
        }
        val lineLength: Int = nextLineStart - lineStart

        when {
          character < 0 -> lineStart
          character >= lineLength -> {
            var pos: Int = lineStart + lineLength

            if (pos >= 1) {
              if (text[pos - 1] == '\r') {
                pos--
              } else if (text[pos - 1] == '\n') {
                pos--
                if ((pos >= 1) && (text[pos - 1] == '\r')) pos--
              }
            }

            pos
          }
          else -> lineStart + character
        }
      }
    }
  }

  fun convertPosition(pos: Int): Position {
    var line: Int = this.lineStartPosList.binarySearch(pos)

    if (line < 0) {
      val insertionPoint: Int = -line - 1
      line = insertionPoint - 1
    }

    return Position(line, pos - this.lineStartPosList[line])
  }

  override fun setText(text: String) {
    super.setText(text)
    reinitializeLineStartPosList(text)
  }
}
