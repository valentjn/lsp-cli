/* Copyright (C) 2021 Julian Valentin, lsp-cli Development Community
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package org.bsplines.lspcli.client

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bsplines.lspcli.server.LspCliLanguageServer
import org.bsplines.lspcli.tools.FileIo
import org.bsplines.lspcli.tools.I18n
import org.bsplines.lspcli.tools.Logging
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LspCliLanguageClient(
  serverCommandLine: List<String>,
  serverWorkingDirPath: Path? = null,
  clientConfigurationFilePath: Path? = null,
) : LanguageClient {
  val languageServerProcess: Process =
      startLanguageServerProcess(serverCommandLine, serverWorkingDirPath)
  val languageServer: LanguageServer = initializeLanguageServer()
  val clientConfiguration: JsonObject = if (clientConfigurationFilePath != null) {
    JsonParser.parseString(FileIo.readFileWithException(clientConfigurationFilePath)).asJsonObject
  } else {
    JsonObject()
  }

  private val _diagnosticsMap: MutableMap<String, List<Diagnostic>> = HashMap()
  val diagnosticsMap: Map<String, List<Diagnostic>>
    get() = _diagnosticsMap

  override fun telemetryEvent(params: Any?) {
  }

  override fun publishDiagnostics(params: PublishDiagnosticsParams?) {
    val uri: String = params?.uri ?: return
    val diagnostics: List<Diagnostic> = params.diagnostics ?: return
    this._diagnosticsMap[uri] = diagnostics
  }

  override fun showMessage(params: MessageParams?) {
  }

  override fun showMessageRequest(
    params: ShowMessageRequestParams?,
  ): CompletableFuture<MessageActionItem> {
    return CompletableFuture.completedFuture(MessageActionItem())
  }

  override fun logMessage(params: MessageParams?) {
  }

  override fun configuration(
    configurationParams: ConfigurationParams?,
  ): CompletableFuture<List<Any>> {
    if (configurationParams == null) return CompletableFuture.completedFuture(emptyList())
    val result = ArrayList<JsonElement>()

    for (ignored: ConfigurationItem in configurationParams.items) {
      result.add(clientConfiguration)
    }

    return CompletableFuture.completedFuture(result)
  }

  companion object {
    private fun startLanguageServerProcess(
      serverCommandLine: List<String>,
      serverWorkingDirPath: Path? = null,
    ): Process {
      val absoluteServerCommandLine: MutableList<String> = serverCommandLine.toMutableList()

      if (serverWorkingDirPath != null) {
        absoluteServerCommandLine[0] =
            serverWorkingDirPath.resolve(absoluteServerCommandLine[0]).toString()
      }

      Logging.logger.info(
        I18n.format(
          "startingLanguageServer",
          absoluteServerCommandLine.joinToString(" "),
          serverWorkingDirPath?.toFile(),
        )
      )

      val processBuilder: ProcessBuilder =
          ProcessBuilder(absoluteServerCommandLine).directory(serverWorkingDirPath?.toFile())
      val process: Process = processBuilder.start()
      Runtime.getRuntime().addShutdownHook(Thread(process::destroy))

      return process
    }
  }

  private fun initializeLanguageServer(): LanguageServer {
    val executorService: ExecutorService = Executors.newSingleThreadScheduledExecutor()

    val launcherBuilder = LSPLauncher.Builder<LspCliLanguageServer>()
    launcherBuilder.setLocalService(this)
    launcherBuilder.setRemoteInterface(LspCliLanguageServer::class.javaObjectType)
    launcherBuilder.setInput(this.languageServerProcess.inputStream)
    launcherBuilder.setOutput(this.languageServerProcess.outputStream)
    launcherBuilder.setExecutorService(executorService)

    val launcher: Launcher<LspCliLanguageServer> = launcherBuilder.create()
    val server: LanguageServer = launcher.remoteProxy
    launcher.startListening()
    executorService.shutdown()

    Logging.logger.info(I18n.format("initializingLanguageServer"))
    server.initialize(InitializeParams()).get()
    return server
  }
}
