/**
 * Bind Layer for Eclipse IDE and Karpfen Framework.
 * It provides EMF resource loading, persistence and diagnostics
 * and integrates Karpfen {@code .kmeta}, {@code .kmodel} and {@code .kdiagram}
 * text files into Eclipse Modeling Framework (EMF) ecosystem and Eclipse IDE.
 * Responsible for T2D (Text to Diagram) and D2T (Diagram to Text)
 * transformations, doSave and doLoad hooks, and diagnostics for lexical,
 * syntactic
 * and structural errors, which are reported as problem markers in Eclipse IDE.
 * <ul>
 * <li>{@code .kmeta} files are loaded as
 * {@link org.karpfen.resource.KmetaResource}.</li>
 * <li>{@code .kmodel} files are loaded as
 * {@link org.karpfen.resource.KmodelResource}.</li>
 * <li>{@code .kdiagram} files are loaded as
 * {@link org.karpfen.resource.KstatesResource}.</li>
 * <li>{@link org.karpfen.resource.KarpfenDiagnostic} provides diagnostics for
 * {@code .kmeta},
 * {@code .kmodel} and {@code .kdiagram} files.</li>
 * </ul>
 */
package org.karpfen.resource;

// https://docs.oracle.com/javase/6/docs/technotes/tools/solaris/javadoc.html#packagecomment