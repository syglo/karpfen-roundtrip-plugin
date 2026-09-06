package org.karpfen.serializer;

/**
 * Strategy mode governing whether Diagram-to-Text (D2T) serialization uses
 * programmatic Java routines
 * or Acceleo 4 code generation templates.
 * Used for benchmarking to compare the performance of both approaches.
 */
public enum SerializerMode {
    /** Direct manual programmatic Java serialization. */
    DIRECT_MANUAL_JAVA,
    /** Acceleo 4 template-based code generation serialization. */
    ACCELEO_TEMPLATE,
}
