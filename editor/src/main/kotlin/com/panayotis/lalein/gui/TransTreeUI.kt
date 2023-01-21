package com.panayotis.lalein.gui

import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode

class TransTreeUI : JTree() {
    init {
        model = DefaultTreeModel(DefaultMutableTreeNode())
    }

    var translationModel: TranslationSet? = null
        set(value) {
            field = value
            if (value == null) {
                model = DefaultTreeModel(DefaultMutableTreeNode())
                return
            }
            val top = DefaultMutableTreeNode()
            value.translations.forEach { tEntry ->
                val transl = DefaultMutableTreeNode(tEntry)
                top += transl
                // Add a sub-entry only if we have more than one translation parameter
                if (tEntry.variables.size > 1)
                    tEntry.variables.forEach { tVar ->
                        val variable = DefaultMutableTreeNode(tVar)
                        transl += variable
                    }
            }
            model = DefaultTreeModel(top)
        }

    private operator fun DefaultMutableTreeNode.plusAssign(child: MutableTreeNode) {
        add(child)
    }
}