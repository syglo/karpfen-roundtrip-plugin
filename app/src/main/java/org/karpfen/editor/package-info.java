/**
 * Bind Layer for Eclipse IDE and Karpfen Framework, providing Karpfen text
 * editor for {@code .kmeta}, {@code .kmodel} and {@code .kstates} files.
 * It provides text editing inside Eclipse IDE, contains Karpfen's ANTLR-based
 * syntax highlighting, and live validation of Karpfen text files, with problem
 * markers for lexical, syntactic and structural errors.
 * <h1>Features</h1>
 * <ul>
 * <li>Syntax Highlighting: streams tokens from ANTLR lexer and adds color
 * coding for keywords, strings, and etc.</li>
 * <li>Live Background Reconciliation: continuously validates content against
 * language rules with short debounce, and places line-accurate error
 * markers.</li>
 * <li>Automatted Formatting on Save</li>
 * </ul>
 */
package org.karpfen.editor;
