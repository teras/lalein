package com.panayotis.lalein.gui

import com.formdev.flatlaf.util.UIScale
import com.panayotis.lalein.Lalein
import com.panayotis.lalein.antlr.asArguments
import java.awt.Color
import java.awt.Dimension
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.JFileChooser.APPROVE_OPTION
import javax.swing.SwingUtilities.invokeLater
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION

class LaleinFrame : JFrame(), MenuActions {
    private val base = LaleinView()
    private val colorOK: Color
    private val colorError: Color = Color.red

    private var cEntry: UITranslationUnit? = null
    private var pathEntry: TreePath? = null
    private var nodeEntry: DefaultMutableTreeNode? = null
    private var cVariable: TranslationVariable? = null
    private var pathVariable: TreePath? = null
    private var nodeVariable: DefaultMutableTreeNode? = null

    private var translationSet
        get() = base.transT.translationModel
        set(value) {
            base.transT.translationModel = value
        }
    private var cachedLalein: Lalein? = null

    init {
        // base.addEntryC.addActionListener { addEntry() }
        // base.addVarC.addActionListener { addVariable() }
        // base.delC.addActionListener { removeEntry() }
        base.transT.addTreeSelectionListener { selectTreePath(it.path) }
        base.loadB.addActionListener { openFile() }
        base.nameF.addTextListener { nameChanged(it) }
        base.variableF.addTextListener { varChanged(it) }
        base.formatF.addTextListener { formatChanged(it) }
        base.zeroF.addTextListener { zeroChanged(it) }
        base.oneF.addTextListener { oneChanged(it) }
        base.twoF.addTextListener { twoChanged(it) }
        base.fewF.addTextListener { fewChanged(it) }
        base.manyF.addTextListener { manyChanged(it) }
        base.otherF.addTextListener { otherChanged(it) }
        base.paramsT.addTextListener { paramsChange(it) }

        /*
         * Hide editable actions
         */
//        base.transT.addMouseListener(object : MouseAdapter() {
//            override fun mouseClicked(e: MouseEvent) {
//                mouseClickedOnEntry(e)
//            }
//        })
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

    private fun mouseClickedOnEntry(e: MouseEvent) {
        if (e.clickCount != 1 || !SwingUtilities.isRightMouseButton(e)) return
        val data = base.transT.getRowForLocation(e.x, e.y)
        val path = base.transT.getPathForLocation(e.x, e.y)
        if (data >= 0 && path != null) {
            base.transT.setSelectionRow(data)
            val items = path.toList
            val last = items.last()
            if (last is UITranslationUnit)
                UnitPopupMenu(last, e, this)
            else if (last is TranslationVariable)
                ParameterPopupMenu(items[items.size - 2] as UITranslationUnit, last, e, this)
        } else
            EmptySpacePopupMenu(e, this)
    }

    private fun selectTreePath(path: TreePath) {
        val node = path.lastPathComponent as DefaultMutableTreeNode
        if (node.parent == null) return // we are deleting a node
        val currentObject = node.userObject
        val oldEntry = cEntry
        if (currentObject is UITranslationUnit) {
            pathEntry = path
            nodeEntry = node
            nodeVariable =
                node.children().let { if (it.hasMoreElements()) it.nextElement() else null } as? DefaultMutableTreeNode?
            pathVariable = nodeVariable?.let { TreePath(it) }
            cEntry = currentObject
            cVariable = currentObject.firstVariable
        } else if (currentObject is TranslationVariable) {
            pathEntry = path.parentPath
            nodeEntry = (node.parent as DefaultMutableTreeNode).run {
                cEntry = userObject as UITranslationUnit
                this
            }
            nodeVariable = node
            pathVariable = path

            cVariable = currentObject
        }
        nodeSelected(cEntry, cVariable, cEntry?.name != oldEntry?.name)
    }


    private fun openFile() {
        with(JFileChooser(defaultFile)) {
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = LaleinFileFilter()
            if (showOpenDialog(null) == APPROVE_OPTION) {
                val file = selectedFile
                defaultFile = file
                title = file.absolutePath
                try {
                    translationSet = TranslationSet(file)
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
            cachedLalein = null
        }
    }

    private fun nodeSelected(entry: UITranslationUnit?, vr: TranslationVariable?, cleanParams: Boolean) {
        val singleParam = entry != null && entry.singleParam
        if (entry == null)
            setEntry()
        else
            setEntry(entry.name, if (singleParam) null else entry.format)
        if (vr == null)
            setVariable(cleanParams)
        else
            setVariable(
                cleanParams,
                if (singleParam) null else vr.name,
                vr.argIndex,
                vr.zero ?: "",
                vr.one ?: "",
                vr.two ?: "",
                vr.few ?: "",
                vr.many ?: "",
                vr.other ?: ""
            )
    }

    private fun showPath(newNode: DefaultMutableTreeNode) {
        val path = TreePath(newNode.path)
        base.transT.selectionPath = path
        base.transT.scrollPathToVisible(path)
    }

    private fun addEntry() {
        val transSet = translationSet ?: return
        val newEntry = UITranslationUnit("", "")
        val newNode = DefaultMutableTreeNode(newEntry)
        transSet.translations += newEntry
        val model = base.transT.model as DefaultTreeModel
        val root = model.root as DefaultMutableTreeNode
        model.insertNodeInto(newNode, root, root.childCount)
        showPath(newNode)
    }

    private fun addVariable() {
        val entry = cEntry ?: return
        val entryPath = nodeEntry ?: return
        val newVar = TranslationVariable("", 1)
        val newNode = DefaultMutableTreeNode(newVar)
        entry.variables += newVar
        val model = base.transT.model as DefaultTreeModel
        model.insertNodeInto(newNode, entryPath, entryPath.childCount)
        showPath(newNode)
    }

    private fun removeEntry() {
        val entry = cEntry
        val nodeE = nodeEntry
        val variable = cVariable
        val nodeV = nodeVariable
        if (entry != null && variable != null && nodeV != null) removeVariable(entry, variable, nodeV)
        else if (entry != null && nodeE != null) removeEntryImpl(entry, nodeE)
        else JOptionPane.showMessageDialog(null, "No selection", null, JOptionPane.ERROR_MESSAGE)
    }

    private fun removeEntryImpl(entry: UITranslationUnit, node: MutableTreeNode) {
        if (JOptionPane.showConfirmDialog(
                null,
                "Do you really want to remove entry\n$'{entry.name}'?",
                null,
                JOptionPane.YES_NO_OPTION
            ) == JOptionPane.YES_OPTION
        ) {
            val model = base.transT.model as DefaultTreeModel
            model.removeNodeFromParent(node)
            translationSet?.translations?.remove(entry)
            setEntry()
            setVariable(true)
        }
    }

    private fun removeVariable(entry: UITranslationUnit, variable: TranslationVariable, nodeVariable: MutableTreeNode) {
        if (JOptionPane.showConfirmDialog(
                null,
                "Do you really want to remove entry\n$'{entry.name}'?",
                null,
                JOptionPane.YES_NO_OPTION
            ) == JOptionPane.YES_OPTION
        ) {
            val model = base.transT.model as DefaultTreeModel
            model.removeNodeFromParent(nodeVariable)
            entry.variables -= variable
            setEntry()
            setVariable(true)
        }
    }

    private fun nameChanged(text: String) {
        cEntry?.name = text
        (base.transT.model as DefaultTreeModel).valueForPathChanged(pathEntry, cEntry)
        base.nameL.foreground = if (text.isBlank()) colorError else colorOK
    }

    private fun formatChanged(text: String) {
        cEntry?.format = text
        base.formatL.foreground = if (text.isBlank()) colorError else colorOK
        retranslate()
    }

    private fun varChanged(text: String) {
        cVariable?.name = text
        (base.transT.model as DefaultTreeModel).valueForPathChanged(pathVariable, cVariable)
        base.variableL.foreground = if (text.isBlank()) colorError else colorOK
        retranslate()
    }

    private fun zeroChanged(text: String, softUpdate: Boolean = true) {
        base.zeroC.isSelected = text.isNotBlank()
        if (softUpdate) {
            cVariable?.zero = text
            retranslate()
        } else
            base.zeroF.text = text
    }

    private fun oneChanged(text: String, softUpdate: Boolean = true) {
        base.oneC.isSelected = text.isNotBlank()
        if (softUpdate) {
            cVariable?.one = text
            retranslate()
        } else
            base.oneF.text = text
    }

    private fun twoChanged(text: String, softUpdate: Boolean = true) {
        base.twoC.isSelected = text.isNotBlank()
        if (softUpdate) {
            cVariable?.two = text
            retranslate()
        } else
            base.twoF.text = text
    }

    private fun fewChanged(text: String, softUpdate: Boolean = true) {
        base.fewC.isSelected = text.isNotBlank()
        if (softUpdate) {
            cVariable?.few = text
            retranslate()
        } else
            base.fewF.text = text
    }

    private fun manyChanged(text: String, softUpdate: Boolean = true) {
        base.manyC.isSelected = text.isNotBlank()
        if (softUpdate) {
            cVariable?.many = text
            retranslate()
        } else
            base.manyF.text = text
    }

    private fun otherChanged(text: String, softUpdate: Boolean = true) {
        base.otherC.isSelected = text.isNotBlank()
        if (softUpdate) {
            cVariable?.other = text
            retranslate()
        } else
            base.otherF.text = text
    }

    private fun retranslate() {
        cachedLalein = null
        paramsChange(base.paramsT?.text ?: return)
    }

    private fun paramsChange(text: String) {
        invokeLater {
            val repo = cachedLalein ?: cEntry?.lalein ?: return@invokeLater
            val args = text.asArguments
            base.paramsL.foreground = if (args == null) colorError else colorOK
            if (args == null) {
                base.resultL.foreground = colorError
                base.resultC.text = "Not valid parameters"
                return@invokeLater
            }
            val (result, success) = try {
                repo.format(cEntry?.name, *args) to true
            } catch (e: Exception) {
                e.message to false
            }
            base.resultC.text = result ?: ""
            base.resultL.foreground = if (success) colorOK else colorError
        }
    }

    override fun addTranslation(unit: UITranslationUnit?) {
        println(unit?.name ?: "-none-")
    }

    override fun removeTranslation(unit: UITranslationUnit) {
        println(unit.name)
    }

    override fun addParameter(unit: UITranslationUnit, param: TranslationVariable?) {
        println("${unit.name} ${param?.name ?: ""}")
    }

    override fun removeParameter(param: TranslationVariable) {
        println(param.name)
    }


}

