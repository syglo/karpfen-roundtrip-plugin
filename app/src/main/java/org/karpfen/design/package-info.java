/**
 * Responsible for visualization of Karpfen {@code .kmeta}, {@code .kmodel} and
 * {@code .kstates} using Eclipse Sirius framework on top of Eclipse IDE.
 * It contains diagram configuration, direct-editing features and programmatic
 * viewpoint definitions, label rendering logic, file generation (.odesign).
 * <h1>Core Sirius concepts</h1>
 * <ul>
 * <li>Viewpoint Generation: Defines {@code karpfen.odesign} specification,
 * for configuring UML Class Diagrams, Object Diagrams, and Statecharts, and
 * defines routing layers (straight vs manhattan).
 * </li>
 * <li>Visual Label Formatting: Translates inmemory EMF model into concise
 * standard UML visual notations for Class Diagrams, Object Diagrams, and
 * Statecharts.
 * </li>
 * <li>
 * Canvas Micro-Editing: Reuses Karpfen ANTRL grammar parsers to validate
 * entered text directly on Sirius canvas elements (shapes/edges), rejecting
 * invalid input or even autocompleting missing elements.
 * </li>
 * <li>
 * Defines {@code KarpfenDiagramService}, which provides programmatic access to
 * Sirius
 * diagram elements, and
 * allows to programmatically create, update and delete diagram elements, and to
 * synchronize diagram elements with underlying EMF model elements.
 * </li>
 * </ul>
 */
package org.karpfen.design;
