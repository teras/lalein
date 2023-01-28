package com.panayotis.lalein.gui

import com.formdev.flatlaf.util.UIScale
import com.panayotis.lalein.Lalein
import com.panayotis.lalein.LaleinInfo
import com.panayotis.lalein.ParameterInfo
import com.panayotis.lalein.TranslationInfo
import com.panayotis.lalein.antlr.asArguments
import java.awt.Color
import java.awt.Dimension
import javax.swing.JFileChooser
import javax.swing.JFileChooser.APPROVE_OPTION
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities.invokeLater
import javax.swing.WindowConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION

class LaleinFrame : JFrame() {
    private val base = LaleinView()
    private val colorOK: Color
    private val colorError: Color = Color.red
    private var lastTranslationInfo: TranslationInfo? = null
    private var lalein: Lalein? = null

    init {
        base.transT.addTreeSelectionListener { selectTreePath(it.path) }
        base.loadB.addActionListener { openFile() }
        base.paramsT.addTextListener { paramsChange(it) }

        base.saveB.isVisible = false

        val scaleFactor = UIScale.getUserScaleFactor()

        base.splitC.resizeWeight = 0.3
        base.transT.selectionModel.selectionMode = SINGLE_TREE_SELECTION
        base.resultC.font = base.resultC.font.deriveFont((base.resultC.font.size2D * 1.2).toFloat())

        contentPane = base.base
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        setLocationRelativeTo(null)
        colorOK = base.nameL.foreground
        setEntry()
        setVariable(true)
        size = Dimension((1000 * scaleFactor).toInt(), (600 * scaleFactor).toInt())
    }

    private fun selectTreePath(path: TreePath) {
        val node = path.lastPathComponent as DefaultMutableTreeNode
        if (node.parent == null) return // we are deleting a node
        val currentObject = node.userObject
        val (translation, parameter) = when (currentObject) {
            is TranslationInfo -> currentObject to currentObject.parameters?.firstOrNull()
            is ParameterInfo -> currentObject.parent to currentObject
            else -> null to null
        }
        nodeSelected(translation, parameter, translation != lastTranslationInfo)
        lastTranslationInfo = translation
    }


    private fun openFile() {
        with(JFileChooser(defaultFile)) {
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = SupportedFileFilter()
            addChoosableFileFilter(LaleinFileFilter("JSON"))
            addChoosableFileFilter(LaleinFileFilter("YAML"))
            addChoosableFileFilter(LaleinFileFilter("Properties"))
            if (showOpenDialog(null) == APPROVE_OPTION) {
                val file = selectedFile
                defaultFile = file
                title = file.absolutePath
                try {
                    lalein = file.lalein
                    base.transT.translationModel = LaleinInfo(lalein)
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(
                        this,
                        e.toString(),
                        "Error opening ${file.name}",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
                with(base.transT) {
                    var row = 0
                    while (row < rowCount)
                        expandRow(row++)
                    if (rowCount > 1)
                        selectTreePath(
                            TreePath(
                                (((model as DefaultTreeModel).root as DefaultMutableTreeNode).children()
                                    .nextElement() as DefaultMutableTreeNode).path
                            )
                        )
                }
            }
        }
    }

    private fun setEntry(name: String? = null, format: String? = null) {
        with(base) {
            nameF.isEnabled = name != null
            nameF.text = name ?: ""
            nameL.foreground = if (name != null && name.isBlank()) colorError else colorOK

            formatF.isEnabled = format != null
            formatF.text = format ?: ""
            formatL.foreground = if (format != null && format.isBlank()) colorError else colorOK

            paramsT.isEnabled = name != null
        }
    }

    private fun setVariable(
        cleanParams: Boolean,
        variable: String? = null,
        argIndex: Int? = null,
        zero: String? = null,
        one: String? = null,
        two: String? = null,
        few: String? = null,
        many: String? = null,
        other: String? = null
    ) {
        with(base) {
            variableF.isEnabled = variable != null
            variableF.text = variable ?: ""
            variableL.foreground = if (variable != null && variable.isBlank()) colorError else colorOK

            zeroC.isSelected = !zero.isNullOrBlank()
            zeroF.isEnabled = zero != null
            zeroF.text = zero ?: ""

            oneC.isSelected = !one.isNullOrBlank()
            oneF.isEnabled = one != null
            oneF.text = one ?: ""

            twoC.isSelected = !two.isNullOrBlank()
            twoF.isEnabled = two != null
            twoF.text = two ?: ""

            fewC.isSelected = !few.isNullOrBlank()
            fewF.isEnabled = few != null
            fewF.text = few ?: ""

            manyC.isSelected = !many.isNullOrBlank()
            manyF.isEnabled = many != null
            manyF.text = many ?: ""

            otherC.isSelected = !other.isNullOrBlank()
            otherF.isEnabled = other != null
            otherF.text = other ?: ""

            if (cleanParams) {
                paramsL.foreground = colorOK
                paramsT.text = ""
                resultL.foreground = colorOK
                resultC.text = ""
            }
        }
    }

    private fun nodeSelected(entry: TranslationInfo?, vr: ParameterInfo?, cleanParams: Boolean) {
        val singleParam = entry != null && entry.parameterCount == 1
        if (entry == null)
            setEntry()
        else
            setEntry(entry.handler, if (singleParam) null else entry.format)
        if (vr == null)
            setVariable(cleanParams)
        else
            setVariable(
                cleanParams,
                if (singleParam) null else vr.handler,
                vr.index,
                vr.zero ?: "",
                vr.one ?: "",
                vr.two ?: "",
                vr.few ?: "",
                vr.many ?: "",
                vr.other ?: ""
            )
    }

    private fun paramsChange(text: String) {
        invokeLater {
            val args = text.asArguments
            base.paramsL.foreground = if (args == null) colorError else colorOK
            if (args == null) {
                base.resultL.foreground = colorError
                base.resultC.text = "Not valid parameters"
                return@invokeLater
            }
            val (result, success) = try {
                lalein?.format(lastTranslationInfo?.handler, *args) to true
            } catch (e: Exception) {
                e.message to false
            }
            base.resultC.text = result ?: ""
            base.resultL.foreground = if (success) colorOK else colorError
        }
    }
}

