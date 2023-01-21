package com.panayotis.lalein.gui

import java.io.File
import java.util.prefs.Preferences

private val prefs = Preferences.userNodeForPackage(TranslationSet::class.java)
private val homeDir = System.getProperty("user.home")
private val File.asExistingFile: File get() = if (exists()) this else parentFile?.asExistingFile ?: File(homeDir)
private const val DEFAULT_FILE = "default_file"

var defaultFile
    get() = File(prefs[DEFAULT_FILE, homeDir]).asExistingFile
    set(value) {
        prefs.put(DEFAULT_FILE, value.absolutePath)
    }