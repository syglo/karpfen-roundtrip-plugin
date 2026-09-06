/**
 * Bridge for Karpfen abstract syntax tree (AST) and Eclipse Modeling Framework
 * (EMF) model.
 * It provides T2D (Text to Diagram) and D2T (Diagram to Text) transformations,
 * by consuming
 * parsed Karpfen AST structures by ANTLR, and constructrs equivalend inmemory
 * EMF models.
 * <h2>Transformation mappings</h2>
 * <ul>
 * <li>{@code .kmeta} files are transformed from {model.KMeta}
 * to EMF model,
 * ClassTypes to {@link org.eclipse.emf.ecore.EClass}, PrimitiveTypes to
 * {@link org.eclipse.emf.ecore.EAttribute},
 * and AssociationTypes to {@link org.eclipse.emf.ecore.EReference}.</li>
 * </li>
 * <li>{@code .kmodel} files are transformed from
 * {model.KModel}
 * to EMF model, runtime dynamic instances of
 * {@link org.eclipse.emf.ecore.EObject} graph based on metamodel schema.
 * </li>
 * <li>{@code .kstates} files are transformed from
 * {model.KStates} to bahavioral EMF model, runtime dynamic
 * instances of
 * {@link org.eclipse.emf.ecore.EObject} graph based on metamodel schema.
 * Hierarchical states, transitions, guard conditions, and action blocks.
 * </li>
 * </ul>
 */
package org.karpfen.transformer;
