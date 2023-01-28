// Generated from Args.g4 by ANTLR 4.9.3
package com.panayotis.lalein.antlr;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link ArgsParser}.
 */
public interface ArgsListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link ArgsParser#args}.
	 * @param ctx the parse tree
	 */
	void enterArgs(ArgsParser.ArgsContext ctx);
	/**
	 * Exit a parse tree produced by {@link ArgsParser#args}.
	 * @param ctx the parse tree
	 */
	void exitArgs(ArgsParser.ArgsContext ctx);
	/**
	 * Enter a parse tree produced by {@link ArgsParser#arg}.
	 * @param ctx the parse tree
	 */
	void enterArg(ArgsParser.ArgContext ctx);
	/**
	 * Exit a parse tree produced by {@link ArgsParser#arg}.
	 * @param ctx the parse tree
	 */
	void exitArg(ArgsParser.ArgContext ctx);
}