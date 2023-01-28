package com.panayotis.lalein.gui

import com.formdev.flatlaf.FlatLightLaf
import com.panayotis.lalein.JsonLalein
import com.panayotis.lalein.Lalein
import com.panayotis.lalein.PropertiesLalein
import com.panayotis.lalein.YamlLalein
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileFilter

fun setupUI() {
    FlatLightLaf.setup()
    UIManager.put("CheckBox.disabledText", UIManager.get("CheckBox.foreground"));
    UIManager.put("CheckBox.icon.disabledCheckmarkColor", UIManager.get("CheckBox.icon.checkmarkColor"))
}

fun JTextField.addTextListener(callback: (String) -> Unit) {
    addKeyListener(object : KeyAdapter() {
        override fun keyTyped(e: KeyEvent) = SwingUtilities.invokeLater { callback(this@addTextListener.text) }
    })
}

class SupportedFileFilter : FileFilter() {
    override fun accept(f: File): Boolean {
        if (!f.isFile) return true
        val name = f.name.lowercase()
        return name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".properties")
    }

    override fun getDescription() = "All Lalein supported files"
}

class LaleinFileFilter(private val name: String) : FileFilter() {
    private val ext = name.lowercase()
    override fun accept(file: File) =
        !file.isFile || file.name.lowercase().endsWith(".$ext")

    override fun getDescription() = "$name files"
}

val File.lalein: Lalein
    get() {
        val name = name.lowercase()
        return when {
            name.endsWith("properties") -> PropertiesLalein.fromFile(this)
            name.endsWith("yaml") -> YamlLalein.fromFile(this)
            else -> JsonLalein.fromFile(this)
        }
    }