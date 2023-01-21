package com.panayotis.lalein.gui

import com.eclipsesource.json.Json
import com.eclipsesource.json.JsonObject
import com.panayotis.lalein.PluralType
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.*

class TranslationSet() {
    constructor(input: File) : this() {
        fun retrieveParam(plurals: JsonObject, baseName: String, index: Int): TranslationVariable {
            val result = TranslationVariable(baseName, index)
            plurals.forEach plural@{ plural ->
                val pluralType = pluralFromTag(plural.name) ?: return@plural
                result.plurals[pluralType] = plural.value.asString()
            }
            return result
        }

        Json.parse(
            InputStreamReader(FileInputStream(input), Charsets.UTF_8)
        ).asObject().forEach { translJ ->
            if (translJ.value.isString) {
                translations += UITranslationUnit(translJ.name, translJ.value.asString())
            } else if (translJ.value.isObject) {
                val translV = translJ.value.asObject()
                if (allChildrenAreStrings(translV)) {
                    val entry = UITranslationUnit(translJ.name, "%{base}")
                    translations += entry
                    entry.variables += retrieveParam(translV, "base", 1)
                } else {
                    val entry = UITranslationUnit(translJ.name, "%{${translV.names().first()}}")
                    translations += entry
                    var previousIndex = 0
                    translV.forEach { paramJ ->
                        val (index, baseName) =
                            if (paramJ.name.startsWith("+")) previousIndex to paramJ.name.substring(1)
                            else (previousIndex + 1) to paramJ.name
                        previousIndex = index
                        entry.variables += retrieveParam(paramJ.value.asObject(), baseName, index)
                    }
                }
            }
        }
    }

    val translations: MutableCollection<UITranslationUnit> = mutableSetOf()
}

fun pluralFromTag(tag: String): PluralType? {
    val lTag = tag.lowercase(Locale.getDefault())
    return PluralType.values().firstOrNull { it.tag == lTag }
}

private fun allChildrenAreStrings(data: JsonObject): Boolean {
    for (key in data.names())
        if (!data[key].isString)
            return false
    return true
}
