/**
 * Provides convertation/serialization of EMF models/graphs into Karpfen text
 * files. (D2T).
 * It contains two ways to serialize: a declarative approach with Acceleo 4 code
 * generation templates
 * {@code .mtl} and direct Java programmatic manual serialization.
 * After convertion it is processed through ANTLR-based code formatter (reusing
 * Karpfen ANTLR grammar parsers) to ensure
 * consistent formatting of {@code .kmeta}, {@code .kmodel} and {@code .kstates}
 * files.
 */
package org.karpfen.serializer;
