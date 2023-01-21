package com.panayotis.lalein.gui

import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

internal class EmptySpacePopupMenu(e: MouseEvent, a: MenuActions) : TransPopupMenu(e, {
    add(JMenuItem("Add new translation").apply { addActionListener { a.addTranslation() } })
})

internal class UnitPopupMenu(tu: UITranslationUnit, e: MouseEvent, a: MenuActions) : TransPopupMenu(e, {
    add(JMenuItem("Add new translation").apply { addActionListener { a.addTranslation(tu) } })
    add(JMenuItem("Remove translation").apply { addActionListener { a.removeTranslation(tu) } })
    add(JMenuItem("Add new parameter").apply { addActionListener { a.addParameter(tu) } })
})

internal class ParameterPopupMenu(tu: UITranslationUnit, tv: TranslationVariable, e: MouseEvent, a: MenuActions) :
    TransPopupMenu(e, {
        add(JMenuItem("Add new translation").apply { addActionListener { a.addTranslation(tu) } })
        add(JMenuItem("Remove translation").apply { addActionListener { a.removeTranslation(tu) } })
        add(JMenuItem("Add new parameter").apply { addActionListener { a.addParameter(tu, tv) } })
        add(JMenuItem("Remove parameter").apply { addActionListener { a.removeParameter(tv) } })
    })

internal sealed class TransPopupMenu(e: MouseEvent, addMenus: TransPopupMenu.() -> Unit) : JPopupMenu() {
    init {
        addMenus()
        layout = null
        isVisible = true
        show(e.component, e.x, e.y)
    }

    final override fun show(invoker: Component?, x: Int, y: Int) {
        super.show(invoker, x, y)
    }
}

interface MenuActions {
    fun addTranslation(unit: UITranslationUnit? = null)
    fun removeTranslation(unit: UITranslationUnit)
    fun addParameter(unit: UITranslationUnit, param: TranslationVariable? = null)
    fun removeParameter(param: TranslationVariable)
}