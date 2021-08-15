<!--
   - Copyright (C) 2021 Julian Valentin, lsp-cli Development Community
   -
   - This Source Code Form is subject to the terms of the Mozilla Public
   - License, v. 2.0. If a copy of the MPL was not distributed with this
   - file, You can obtain one at https://mozilla.org/MPL/2.0/.
   -->

# `lsp-cli` — CLI Language Client for LSP Language Servers

`lsp-cli` implements a language client according to the [Language Server Protocol (LSP)](https://microsoft.github.io/language-server-protocol/) with a command-line interface (CLI).

Language servers written for the LSP can usually only be used with a language client, which is typically an editor like VS Code. With `lsp-cli`, language servers can also be used on the command line. This allows you to harness the power of language servers for scripting, build pipelines, etc.

Most [existing language servers](https://microsoft.github.io/language-server-protocol/implementors/servers/) should be supported, but `lsp-cli` was created for [LT<sub>E</sub>X LS](https://github.com/valentjn/ltex-ls) (a language server for LanguageTool), which is primary use case of `lsp-cli` and drives its development.

## Features

- Printing diagnostics (linting) for each checked file
- Printing code actions for each diagnostic
- Supplying client configuration to the server
- Customization of usage message and default argument values via JSON file (path supplied by environment variable)

If you'd like support for other LSP features as well, please open a feature request or a pull request.

## Known Limitations

- Client-side requests of diagnostics are currently not supported by the LSP (see [microsoft/language-server-protocol#737](https://github.com/microsoft/language-server-protocol/issues/737) and [current proposal for LSP 3.17](https://github.com/microsoft/vscode-languageserver-node/blob/eba6a7308b21ab94bd412fbfa63e36964b6d82ad/protocol/src/common/proposed.diagnostics.md)). Therefore, `lsp-cli` relies on the server sending a `textDocument/publishDiagnostics` notification for every opened file, even if there are no diagnostics.

## Requirements

- 64-bit Linux, Mac, or Windows operating system; alternatively, an arbitrary operating system with Java installed

## Installation

1. Download the [latest release](https://github.com/valentjn/lsp-cli/releases/latest) from GitHub.
   - It's recommended that you choose the archive corresponding to your platform (these archives are standalone, no Java installation necessary).
   - If you choose the platform-independent file `lsp-cli-VERSION.tar.gz`, then you need Java 11 or later on your computer.
2. Extract the archive to an arbitrary location on your computer.

## Startup

It is recommended to use the startup scripts `bin/lsp-cli` (Linux, Mac) and `bin\lsp-cli.bat` (Windows) to start `lsp-cli`. These scripts are only part of the released versions. The startup scripts can be controlled by the following environment variables:

- `JAVA_HOME`: Path to the directory of the Java distribution to use (contains `bin`, `lib`, and other subdirectories). If set, this overrides the included Java distribution when using a platform-dependent `lsp-cli` archive.
- `JAVA_OPTS`: Java arguments to be fed to `java` (e.g., `-Xmx1024m`)

It is also possible to start `lsp-cli` directly without the startup scripts (not recommended).

### Command-Line Arguments

Any command-line arguments supplied to the startup scripts are processed by `lsp-cli` itself. The possible arguments are as follows:

- `--client-configuration=<file>`: Use the client configuration stored in the JSON file `<file>`. The format is usually nested JSON objects (e.g., `{"latex": {"commands": ...}}`).\
  Only for LT<sub>E</sub>X LS: A flattened JSON object (`{"latex.commands": ...}`) is also allowed. Setting names may be prefixed by a top level named `ltex` (e.g., `{"ltex.latex.commands": ...}` is accepted as well).
- `-h`, `--help`: Show help message and exit.
- `--hide-commands`: Hide commands in lists of code actions for diagnostics, only show quick fixes.
- `--input-documents=<path> <path> ...`: Instead of running as server, check the documents at the given paths, print the results to standard output, and exit. Directories are traversed recursively. If `-` is given, standard input will be checked as plain text.
- `--server-command-line=<string>`: Required. Command line to start the language server, starting with the path of its executable. If you want to supply arguments to the language server, separate them with spaces. If the path of the executable or one of the arguments contain spaces, you can escape them by using `\ ` instead. In `.lsp-cli.json`, this option can either be specified as an array of arguments or as a string with space-separated arguments.
- `--server-working-directory=<directory>`: Working directory for `--server-command-line`. If omitted, use the parent directory of `.lsp-cli.json` if given, otherwise use the current working directory.
- `-V`, `--version`: Print version information as JSON to the standard output and exit. The format is a JSON object with `"java"` and `"lsp-cli"` keys and string values. A key may be missing if no information about the corresponding version could be retrieved.
- `--verbose`: Write to standard error output what is being done.
- `<path> <path> ...`

Instead of using the equals sign `=` to separate option names and values, it is also possible to use one or more spaces.

### `.lsp-cli.json` Configuration File

The appearance of help messages (e.g., `--help`) and the default values of arguments can also be controlled via a special JSON file, which is usually named `.lsp-cli.json`.

The file is located via checking the environment variable `LSP_CLI_JSON_SETTINGS_PATH`. If this path points to a file, then that file will be used. If it points to a directory, then the file named `.lsp-cli.json` in that directory will be used. The behavior of `lsp-cli` is controlled by the command-line arguments as usual, if `LSP_CLI_JSON_SETTINGS_PATH` is not set or the path it contains does not exist.

The JSON file is given below in TypeScript. When specifying arguments, always use their full names as strings (e.g., `"--server-working-directory"`).

```typescript
interface LspCliJson {
  /**
   * Program name to use in help and error messages (default: `lsp-cli`).
   */
  programName?: string;

  helpMessage?: {
    /**
     * Description of the program.
     */
    description?: string;

    /**
     * List of arguments to hide.
     * If both `hiddenArguments` and `visibleArguments` are given, `visibleArguments` wins.
     */
    hiddenArguments?: string[];

    /**
     * List of arguments to show; hide all other arguments.
     * If both `hiddenArguments` and `visibleArguments` are given, `visibleArguments` wins.
     */
    visibleArguments?: string[];
  };

  defaultValues?: {
    /**
     * Default values for arguments, if they are not specified on the command line.
     * The types of the values depend on the arguments (e.g., use `true` for Boolean flags).
     */
    [argument: string]: any;
  };
}
```

### Exit Codes

- 0: `lsp-cli` exited successfully, and the language server reported no diagnostics for the checked files.
- 1: An exception was thrown during the execution of `lsp-cli`.
- 2: An invalid command-line argument was supplied to `lsp-cli`.
- 3: The language server reported at least one diagnostic for the checked files.
