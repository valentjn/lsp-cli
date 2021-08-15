/* Copyright (C) 2021 Julian Valentin, lsp-cli Development Community
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package org.bsplines.lspcli.tools

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.bsplines.lspcli.LspCliLauncher
import picocli.CommandLine

class VersionProvider : CommandLine.IVersionProvider {
  override fun getVersion(): Array<String> {
    val lspcliPackage: Package? = LspCliLauncher::class.java.getPackage()
    val jsonObject = JsonObject()

    if (lspcliPackage != null) {
      val lspcliVersion: String? = lspcliPackage.implementationVersion
      if (lspcliVersion != null) jsonObject.addProperty("lsp-cli", lspcliVersion)
    }

    val javaVersion: String? = System.getProperty("java.version")
    if (javaVersion != null) jsonObject.addProperty("java", javaVersion)

    val gsonBuilder: Gson = GsonBuilder().setPrettyPrinting().create()
    return arrayOf(gsonBuilder.toJson(jsonObject))
  }
}
