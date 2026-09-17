package com.training.document.entity;

/**
 * Lowercase constant names by design: this is the literal 2-char code used both on the wire
 * (JSON) and in the DB via {@code @Enumerated(EnumType.STRING)} — no separate code/converter needed.
 */
public enum DocType {
    rt,
    tp
}
