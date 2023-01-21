package com.panayotis.lalein.gui


import com.panayotis.lalein.Lalein
import com.panayotis.lalein.Parameter
import com.panayotis.lalein.TranslationUnit

class UITranslationUnit(
    internal var name: String,
    internal var format: String,
) : TranslationUnit {
    override fun hashCode() = name.hashCode()
    override fun equals(other: Any?) = if (other is UITranslationUnit) name == other.name else false
    override fun toString() = name

    override fun getHandler() = name
    override fun getFormat() = format
    override fun getParameters() =
        variables.map { Parameter(it.name, it.argIndex, it.zero, it.one, it.two, it.few, it.many, it.other) }

    val variables: MutableCollection<TranslationVariable> = mutableSetOf()
    val firstVariable get() = if (variables.isEmpty()) null else variables.iterator().next()
    val singleParam get() = variables.size == 1

    val lalein get() = Lalein(listOf(this))

}