package com.panayotis.lalein.antlr

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.atn.ATNConfigSet
import org.antlr.v4.runtime.dfa.DFA
import org.antlr.v4.runtime.tree.ParseTreeWalker
import java.util.*

val String.asArguments: Array<Any>?
    get() {
        if (this.isBlank()) return emptyArray()
        val result = ArgsParsingListener()
        val lexer = ArgsLexer(CharStreams.fromString(this))
        val parser = ArgsParser(CommonTokenStream(lexer))
        val errorRes = redirectParser(lexer, parser)
        ParseTreeWalker().walk(result, parser.args())
        return if (errorRes().isEmpty()) result.args.toTypedArray() else null
    }

private fun redirectParser(lexer: Lexer, parser: Parser): () -> Collection<String> {
    val errors = mutableListOf<String>()

    class ErrorListener : BaseErrorListener() {
        private val errors: MutableCollection<String> = mutableListOf()
        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String?,
            e: RecognitionException?
        ) {
            errors += "line $line:$charPositionInLine $msg"
        }

        override fun reportAmbiguity(
            recognizer: Parser?,
            dfa: DFA?,
            startIndex: Int,
            stopIndex: Int,
            exact: Boolean,
            ambigAlts: BitSet?,
            configs: ATNConfigSet?
        ) {
            println("C")
        }

        override fun reportAttemptingFullContext(
            recognizer: Parser?,
            dfa: DFA?,
            startIndex: Int,
            stopIndex: Int,
            conflictingAlts: BitSet?,
            configs: ATNConfigSet?
        ) {
            println("X")
        }

        override fun reportContextSensitivity(
            recognizer: Parser?,
            dfa: DFA?,
            startIndex: Int,
            stopIndex: Int,
            prediction: Int,
            configs: ATNConfigSet?
        ) {
            println("Q")
        }
    }
    parser.removeErrorListeners()
    lexer.removeErrorListeners()
    val listener = ErrorListener()
    lexer.addErrorListener(listener)
    parser.addErrorListener(listener)
    return { errors }
}

private class ArgsParsingListener : ArgsBaseListener() {
    val args = mutableListOf<Any>()
    override fun exitArg(ctx: ArgsParser.ArgContext) {
        args += when {
            ctx.FLOAT() != null -> ctx.FLOAT().text.toDouble()
            ctx.STRING() != null -> ctx.STRING().text.let { it.substring(1, it.length - 1) }
            ctx.INT() != null -> try {
                ctx.INT().text.toInt()
            } catch (e: Exception) {
                ctx.INT().text.toLong()
            }

            else -> return
        }
    }
}