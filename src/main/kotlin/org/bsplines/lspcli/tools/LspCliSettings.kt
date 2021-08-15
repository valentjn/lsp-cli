package org.bsplines.lspcli.tools

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Path

class LspCliSettings(
  val settingsFilePath: Path? = null,
) {
  val jsonSettings: JsonObject = if (settingsFilePath != null) {
    JsonParser.parseString(FileIo.readFileWithException(settingsFilePath)).asJsonObject
  } else {
    JsonObject()
  }

  fun getValue(vararg keys: String): JsonElement? {
    var jsonElement: JsonElement = this.jsonSettings

    for (key: String in keys) {
      val jsonObject: JsonObject? = if (jsonElement.isJsonObject) jsonElement.asJsonObject else null

      if (jsonObject?.has(key) == true) {
        jsonElement = jsonObject.get(key)
      } else {
        return null
      }
    }

    return jsonElement
  }

  fun getValueAsListOfString(vararg keys: String): List<String>? {
    val jsonElement: JsonElement = getValue(*keys) ?: return null
    val list = ArrayList<String>()

    for (entryElement: JsonElement in jsonElement.asJsonArray) {
      list.add(entryElement.asString)
    }

    return list
  }

  fun getValueAsMap(vararg keys: String): Map<String, JsonElement>? {
    val jsonElement: JsonElement = getValue(*keys) ?: return null
    val map = HashMap<String, JsonElement>()

    for ((mapKey: String, mapValueElement: JsonElement) in jsonElement.asJsonObject.entrySet()) {
      map[mapKey] = mapValueElement
    }

    return map
  }
}
