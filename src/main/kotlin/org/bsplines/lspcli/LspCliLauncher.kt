/* Copyright (C) 2021 Julian Valentin, lsp-cli Development Community
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package org.bsplines.lspcli

import com.google.gson.JsonElement
import org.bsplines.lspcli.client.Checker
import org.bsplines.lspcli.client.LspCliLanguageClient
import org.bsplines.lspcli.tools.I18n
import org.bsplines.lspcli.tools.Logging
import org.bsplines.lspcli.tools.LspCliSettings
import org.bsplines.lspcli.tools.VersionProvider
import org.fusesource.jansi.AnsiConsole
import picocli.CommandLine
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.logging.Level
import kotlin.system.exitProcess

@CommandLine.Command(
  name = "lsp-cli",
  mixinStandardHelpOptions = true,
  showDefaultValues = true,
  versionProvider = VersionProvider::class,
  description = ["lsp-cli - CLI language client for LSP language servers"],
)
class LspCliLauncher : Callable<Int> {
  @CommandLine.Option(names = ["--server-command-line"], paramLabel = "<string>", required = true,
    description = [
      "Required. Command line to start the language server, starting with the path of its "
      + "executable. If you want to supply arguments to the language server, separate them with "
      + "spaces. If the path of the executable or one of the arguments contain spaces, you can "
      + "escape them by using '\\ ' instead. In .lsp-cli.json, this option can either be "
      + "specified as an array of arguments or as a string with space-separated arguments."
    ],
  )
  var serverCommandLineString: String? = null

  var serverCommandLineList: List<String>? = null

  @CommandLine.Option(names = ["--server-working-directory"], paramLabel = "<directory>",
    description = [
      "Working directory for --server-command-line. If omitted, use the parent directory of "
      + "`.lsp-cli.json` if given, otherwise use the current working directory."
    ],
  )
  var serverWorkingDirPath: Path? = null

  @CommandLine.Option(names = ["--client-configuration"], paramLabel = "<file>", description = [
    "Use the client configuration stored in the JSON file <file>. The format is usually nested "
    + "JSON objects (e.g., {\"latex\": {\"commands\": ...}})."
  ])
  var clientConfigurationFilePath: Path? = null

  @CommandLine.Option(names = ["--hide-commands"], description = [
    "Hide commands in lists of code actions for diagnostics, only show quick fixes."
  ])
  var hideCommands: Boolean = false

  @CommandLine.Option(names = ["--verbose"], description = [
    "Write to standard error output what is being done."
  ])
  var verbose: Boolean = false

  @CommandLine.Parameters(paramLabel = "<path>", arity = "1..*", description = [
    "Paths of files or directories to check. "
    + "Directories are traversed recursively for supported file types. "
    + "If - is given, standard input will be checked as plain text."
  ])
  var inputFilePaths: List<Path> = emptyList()

  constructor()

  constructor(parseResult: CommandLine.ParseResult, lspCliSettings: LspCliSettings) {
    if (parseResult.hasMatchedOption("--server-command-line")) {
      this.serverCommandLineString = parseResult.matchedOptionValue("--server-command-line", null)
    } else {
      val jsonElement: JsonElement? =
          lspCliSettings.getValue("defaultValues", "--server-command-line")

      if (jsonElement?.isJsonArray == true) {
        this.serverCommandLineList = jsonElement.asJsonArray.map { it.asString }
      } else if (jsonElement != null) {
        this.serverCommandLineString = jsonElement.asString
      }
    }

    this.serverWorkingDirPath = if (parseResult.hasMatchedOption("--server-working-directory")) {
      parseResult.matchedOptionValue("--server-working-directory", null)
    } else {
      val string: String? = lspCliSettings.getValue(
          "defaultValues",
          "--server-working-directory",
      )?.asString

      if (string != null) {
        Path.of(string)
      } else {
        lspCliSettings.settingsFilePath?.toAbsolutePath()?.parent
      }
    }

    this.clientConfigurationFilePath = if (parseResult.hasMatchedOption("--client-configuration")) {
      parseResult.matchedOptionValue("--client-configuration", null)
    } else {
      lspCliSettings.getValue(
          "defaultValues",
          "--client-configuration",
      )?.asString?.let { Path.of(it) }
    }

    this.hideCommands = if (parseResult.hasMatchedOption("--hide-commands")) {
      parseResult.matchedOptionValue("--hide-commands", false)
    } else {
      lspCliSettings.getValue("defaultValues", "--hide-commands")?.asBoolean ?: false
    }

    this.verbose = if (parseResult.hasMatchedOption("--verbose")) {
      parseResult.matchedOptionValue("--verbose", false)
    } else {
      lspCliSettings.getValue("defaultValues", "--verbose")?.asBoolean ?: false
    }

    if (parseResult.matchedPositionals().isNotEmpty()) {
      this.inputFilePaths = parseResult.matchedPositionals()[0].getValue()
    }
  }

  override fun call(): Int {
    if (this.verbose) Logging.setLogLevel(Level.INFO)

    val serverCommandLine: List<String> = (
      this.serverCommandLineList ?: this.serverCommandLineString?.let {
        serverCommandLineString: String ->
        NON_ESCAPED_SPACE_REGEX.split(serverCommandLineString).map {
          ESCAPED_SPACE_REGEX.replace(it, " ")
        }
      } ?: throw IllegalArgumentException(
        I18n.format("requiredArgumentNotSpecified", "--server-command-line"),
      )
    )

    val client = LspCliLanguageClient(
      serverCommandLine,
      this.serverWorkingDirPath,
      this.clientConfigurationFilePath,
    )
    val checker = Checker(client, hideCommands = this.hideCommands)
    val numberOfMatches: Int = checker.check(this.inputFilePaths)
    client.languageServer.shutdown()
    return (if (numberOfMatches == 0) 0 else EXIT_CODE_MATCHES_FOUND)
  }

  companion object {
    private const val EXIT_CODE_MATCHES_FOUND = 3

    private val ESCAPED_SPACE_REGEX = Regex("\\\\ ")
    private const val NON_ESCAPED_SPACE_REGEX_STRING = "(?<!\\\\) "
    private val NON_ESCAPED_SPACE_REGEX = Regex(NON_ESCAPED_SPACE_REGEX_STRING)

    @JvmStatic
    @Suppress("PrintStackTrace", "SpreadOperator", "TooGenericExceptionCaught")
    fun main(arguments: Array<String>) {
      AnsiConsole.systemInstall()

      val lspCliSettingsFilePath: Path? = getLspCliSettingsFilePath()
      val lspCliSettings = LspCliSettings(lspCliSettingsFilePath)

      val commandLine: CommandLine = createCommandLine(lspCliSettings)
      val commandSpec: CommandLine.Model.CommandSpec = commandLine.commandSpec

      try {
        val parseResult: CommandLine.ParseResult = commandLine.parseArgs(*arguments)

        if (parseResult.isUsageHelpRequested) {
          commandLine.usage(commandLine.out)
          exitProcess(commandSpec.exitCodeOnUsageHelp())
        } else if (parseResult.isVersionHelpRequested) {
          commandLine.printVersionHelp(commandLine.out)
          exitProcess(commandSpec.exitCodeOnVersionHelp())
        }

        val launcher = LspCliLauncher(parseResult, lspCliSettings)
        val exitCode: Int = launcher.call()
        if (exitCode != 0) exitProcess(exitCode)
      } catch (e: CommandLine.ParameterException) {
        commandLine.err.println(e.message)

        if (!CommandLine.UnmatchedArgumentException.printSuggestions(e, commandLine.err)) {
          e.commandLine.usage(commandLine.err)
        }

        exitProcess(commandSpec.exitCodeOnInvalidInput())
      } catch (e: Exception) {
        e.printStackTrace(commandLine.err)
        exitProcess(commandSpec.exitCodeOnExecutionException())
      }
    }

    private fun getLspCliSettingsFilePath(): Path? {
      val environmentVariable: String = System.getenv("LSP_CLI_JSON_SETTINGS_PATH") ?: ""
      val path: Path? = if (environmentVariable.isNotEmpty()) {
        Path.of(environmentVariable)
      } else {
        val appHome: String = System.getProperty("app.home", "")
        if (appHome.isNotEmpty()) Path.of(appHome, "bin") else null
      }

      return if (path == null) {
        null
      } else if (path.toFile().isFile) {
        path
      } else if (path.toFile().isDirectory) {
        val filePath: Path = path.resolve(".lsp-cli.json")
        if (filePath.toFile().isFile) filePath else null
      } else {
        null
      }
    }

    private fun createCommandLine(lspCliSettings: LspCliSettings): CommandLine {
      val defaultValues: Map<String, Any>? = lspCliSettings.getValueAsMap("defaultValues")
      val hiddenArguments: List<String>? =
          lspCliSettings.getValueAsListOfString("helpMessage", "hiddenArguments")
      val visibleArguments: List<String>? =
          lspCliSettings.getValueAsListOfString("helpMessage", "visibleArguments")

      val classCommandSpec = CommandLine.Model.CommandSpec.forAnnotatedObject(LspCliLauncher())
      val commandSpec = CommandLine.Model.CommandSpec.create()

      commandSpec.usageMessage(classCommandSpec.usageMessage())

      val usageMessageDescription: String? =
          lspCliSettings.getValue("helpMessage", "description")?.asString

      if (usageMessageDescription != null) {
        commandSpec.usageMessage().description(usageMessageDescription)
      }

      for (classOptionSpec: CommandLine.Model.OptionSpec in classCommandSpec.options()) {
        commandSpec.addOption(
          createOptionSpec(classOptionSpec, defaultValues, hiddenArguments, visibleArguments)
        )
      }

      for (
        positionalParamSpec: CommandLine.Model.PositionalParamSpec
        in classCommandSpec.positionalParameters()
      ) {
        commandSpec.addPositional(positionalParamSpec)
      }

      val commandLine = CommandLine(commandSpec)
      commandLine.commandName = lspCliSettings.getValue("programName")?.asString ?: "lsp-cli"
      commandLine.isCaseInsensitiveEnumValuesAllowed = true

      return commandLine
    }

    private fun createOptionSpec(
      classOptionSpec: CommandLine.Model.OptionSpec,
      defaultValues: Map<String, Any>?,
      hiddenArguments: List<String>?,
      visibleArguments: List<String>?,
    ): CommandLine.Model.OptionSpec {
      val optionSpecBuilder: CommandLine.Model.OptionSpec.Builder = classOptionSpec.toBuilder()

      if (defaultValues != null) {
        for (name: String in optionSpecBuilder.names()) {
          val defaultValueString: String? = defaultValues[name]?.toString()
          if (defaultValueString != null) optionSpecBuilder.defaultValue(defaultValueString)
        }
      }

      val hidden = if (visibleArguments != null) {
        !visibleArguments.contains(classOptionSpec.longestName())
      } else {
        hiddenArguments?.contains(classOptionSpec.longestName()) ?: false
      }

      optionSpecBuilder.hidden(hidden)
      return optionSpecBuilder.build()
    }
  }
}
