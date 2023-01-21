package com.panayotis.lalein.gui

import com.formdev.flatlaf.FlatLightLaf
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import java.util.*
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileFilter
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath


fun UISetUp() {
    FlatLightLaf.setup()
    UIManager.put("CheckBox.disabledText", UIManager.get("CheckBox.foreground"));
    UIManager.put("CheckBox.icon.disabledCheckmarkColor", UIManager.get("CheckBox.icon.checkmarkColor"))
}

fun JTextField.addTextListener(callback: (String) -> Unit) {
    addKeyListener(object : KeyAdapter() {
        override fun keyTyped(e: KeyEvent) = SwingUtilities.invokeLater { callback(this@addTextListener.text) }
    })
}

val TreePath.toList: List<Any>
    get() {
        val result = mutableListOf<Any>()
        for (i in 0 until pathCount) {
            result += (getPathComponent(i) as DefaultMutableTreeNode).userObject
        }
        return result
    }

class LaleinFileFilter : FileFilter() {
    override fun accept(file: File) =
        !file.isFile || file.name.lowercase(Locale.getDefault()).endsWith(".json")

    override fun getDescription() = "JSON files"
}