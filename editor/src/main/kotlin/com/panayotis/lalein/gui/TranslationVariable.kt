package com.panayotis.lalein.gui

import com.panayotis.lalein.PluralType
import com.panayotis.lalein.PluralType.*

class TranslationVariable(
    internal var name: String,
    argIndex: Int
) {
    override fun hashCode() = name.hashCode()
    override fun equals(other: Any?): Boolean = if (other is TranslationVariable) name == other.name else false
    override fun toString() = "($argIndex) $name"

    val plurals = mutableMapOf<PluralType, String>()

    private fun update(type: PluralType, value: String?) {
        if (value.isNullOrBlank())
            plurals.remove(type)
        else
            plurals[type] = value
    }

    internal var argIndex: Int = argIndex
        set(value) {
            val old = field
            if (old == value) return
            zero = normalizeRef(zero, old, value)
            one = normalizeRef(one, old, value)
            two = normalizeRef(two, old, value)
            few = normalizeRef(few, old, value)
            many = normalizeRef(many, old, value)
            other = normalizeRef(other, old, value)
            field = value
        }

    var zero
        get() = plurals[ZERO]
        set(value) = update(ZERO, value)
    var one
        get() = plurals[ONE]
        set(value) = update(ONE, value)
    var two
        get() = plurals[TWO]
        set(value) = update(TWO, value)
    var few
        get() = plurals[FEW]
        set(value) = update(FEW, value)
    var many
        get() = plurals[MANY]
        set(value) = update(MANY, value)
    var other
        get() = plurals[OTHER]
        set(value) = update(OTHER, value)

    private fun normalizeRef(format: String?, old: Int, new: Int) =
        format?.replace("%$old$", "%$new$")
}