#!/usr/bin/python3

# Copyright (C) 2021 Julian Valentin, lsp-cli Development Community
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

import pathlib
import re
import shutil
import subprocess
import tarfile
import tempfile
import urllib.parse
import urllib.request
import zipfile

javaVersion = "11.0.12+7"



def createBinaryArchive(platform: str, arch: str) -> None:
  print(f"Processing platform/arch '{platform}/{arch}'...")
  lspCliVersion = getLspCliVersion()
  targetDirPath = pathlib.Path(__file__).parent.parent.joinpath("target")
  lspCliArchivePath = pathlib.Path(__file__).parent.parent.joinpath(
      targetDirPath, f"lsp-cli-{lspCliVersion}.tar.gz")

  with tempfile.TemporaryDirectory() as tmpDirPathStr:
    tmpDirPath = pathlib.Path(tmpDirPathStr)

    print("Extracting lsp-cli archive...")
                                                             
                                                             import os
                                                             
                                                             def is_within_directory(directory, target):
                                                                 
                                                                 abs_directory = os.path.abspath(directory)
                                                                 abs_target = os.path.abspath(target)
                                                             
                                                                 prefix = os.path.commonprefix([abs_directory, abs_target])
                                                                 
                                                                 return prefix == abs_directory
                                                             
                                                             def safe_extract(tar, path=".", members=None, *, numeric_owner=False):
                                                             
                                                                 for member in tar.getmembers():
                                                                     member_path = os.path.join(path, member.name)
                                                                     if not is_within_directory(path, member_path):
                                                                         raise Exception("Attempted Path Traversal in Tar File")
                                                             
                                                                 tar.extractall(path, members, numeric_owner=numeric_owner) 
                                                                 
                                                             
                                                             safe_extract(tarFile, path=tmpDirPath)

    lspCliDirPath = tmpDirPath.joinpath(f"lsp-cli-{lspCliVersion}")
    relativeJavaDirPath = downloadJava(tmpDirPath, lspCliDirPath, platform, arch)

    print("Setting default for JAVA_HOME in startup script...")

    if platform == "windows":
      lspCliDirPath.joinpath("bin", "lsp-cli").unlink()
      binScriptPath = lspCliDirPath.joinpath("bin", "lsp-cli.bat")
      searchPattern = re.compile("^set REPO=.*$", flags=re.MULTILINE)
    else:
      lspCliDirPath.joinpath("bin", "lsp-cli.bat").unlink()
      binScriptPath = lspCliDirPath.joinpath("bin", "lsp-cli")
      searchPattern = re.compile("^BASEDIR=.*$", flags=re.MULTILINE)

    with open(binScriptPath, "r") as file: binScript = file.read()

    if platform == "windows":
      insertStr = f"\r\nif not defined JAVA_HOME set JAVA_HOME=\"%BASEDIR%\\{relativeJavaDirPath}\""
    else:
      insertStr = f"\n[ -z \"$JAVA_HOME\" ] && JAVA_HOME=\"$BASEDIR\"/{relativeJavaDirPath}"

    regexMatch = searchPattern.search(binScript)
    assert regexMatch is not None
    binScript = binScript[:regexMatch.end()] + insertStr + binScript[regexMatch.end():]
    with open(binScriptPath, "w") as file: file.write(binScript)

    lspCliBinaryArchiveFormat = ("zip" if platform == "windows" else "gztar")
    lspCliBinaryArchiveExtension = (".zip" if platform == "windows" else ".tar.gz")
    lspCliBinaryArchivePath = targetDirPath.joinpath(
        f"lsp-cli-{lspCliVersion}-{platform}-{arch}")
    print(f"Creating binary archive '{lspCliBinaryArchivePath}{lspCliBinaryArchiveExtension}'...")
    shutil.make_archive(str(lspCliBinaryArchivePath), lspCliBinaryArchiveFormat,
        root_dir=tmpDirPath)
    print("")



def downloadJava(tmpDirPath: pathlib.Path, lspCliDirPath: pathlib.Path,
      platform: str, arch: str) -> str:
  javaArchiveExtension = (".zip" if platform == "windows" else ".tar.gz")
  javaArchiveName = (f"OpenJDK11U-jdk_{arch}_{platform}_hotspot_"
      f"{javaVersion.replace('+', '_')}{javaArchiveExtension}")

  javaUrl = ("https://github.com/adoptium/temurin11-binaries/releases/download/"
      f"jdk-{urllib.parse.quote_plus(javaVersion)}/{javaArchiveName}")
  javaArchivePath = lspCliDirPath.joinpath(javaArchiveName)
  print(f"Downloading JDK from '{javaUrl}' to '{javaArchivePath}'...")
  urllib.request.urlretrieve(javaUrl, javaArchivePath)
  print("Extracting JDK archive...")

  if javaArchiveExtension == ".zip":
    with zipfile.ZipFile(javaArchivePath, "r") as zipFile: zipFile.extractall(path=tmpDirPath)
  else:
                                                           def is_within_directory(directory, target):
                                                               
                                                               abs_directory = os.path.abspath(directory)
                                                               abs_target = os.path.abspath(target)
                                                           
                                                               prefix = os.path.commonprefix([abs_directory, abs_target])
                                                               
                                                               return prefix == abs_directory
                                                           
                                                           def safe_extract(tar, path=".", members=None, *, numeric_owner=False):
                                                           
                                                               for member in tar.getmembers():
                                                                   member_path = os.path.join(path, member.name)
                                                                   if not is_within_directory(path, member_path):
                                                                       raise Exception("Attempted Path Traversal in Tar File")
                                                           
                                                               tar.extractall(path, members, numeric_owner=numeric_owner) 
                                                               
                                                           
                                                           safe_extract(tarFile, path=tmpDirPath)

  print("Removing JDK archive...")
  javaArchivePath.unlink()

  relativeJavaDirPathString = f"jdk-{javaVersion}"
  jdkDirPath = tmpDirPath.joinpath(relativeJavaDirPathString)
  jmodsDirPath = (jdkDirPath.joinpath("jmods") if platform == "mac" else
      jdkDirPath.joinpath("Contents", "Home", "jmods"))
  javaTargetDirPath = lspCliDirPath.joinpath(relativeJavaDirPathString)

  print("Creating Java distribution...")
  subprocess.run(["jlink", "--module-path", str(jmodsDirPath), "--add-modules", "java.se",
      "--strip-debug", "--no-man-pages", "--no-header-files", "--compress=2",
      "--output", str(javaTargetDirPath)])

  print("Removing JDK directory...")
  shutil.rmtree(jdkDirPath)

  return relativeJavaDirPathString



def getLspCliVersion() -> str:
  with open("pom.xml", "r") as file:
    regexMatch = re.search(r"<version>(.*?)</version>", file.read())
    assert regexMatch is not None
    return regexMatch.group(1)



def main() -> None:
  createBinaryArchive("linux", "x64")
  createBinaryArchive("mac", "x64")
  createBinaryArchive("windows", "x64")


if __name__ == "__main__":
  main()
