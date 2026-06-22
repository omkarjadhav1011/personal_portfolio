package com.portfolio.chatbot;

import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase B4 — corpus boundary by construction. The AI may only ever see the curated PUBLIC fields
 * in {@link PortfolioContext}; anything sensitive must never enter that shape. This test reflects
 * over the whole record tree and fails if a field name looks like a secret, raw byte payload, or
 * internal column — so a future leaky field (e.g. adding {@code passwordHash} or {@code resumeData}
 * to a summary record) is caught at build time, not in production.
 */
class CorpusBoundaryTest {

    /** Lowercased field-name fragments that must NEVER appear anywhere in the corpus shape. */
    private static final List<String> FORBIDDEN = List.of(
            "password", "secret", "hash", "apikey", "token", "credential",
            "avatardata", "resumedata", "rawbytes", "bytes",
            "createdat", "updatedat", "deletedat", "internal");

    @Test
    void corpusShapeExposesNoSensitiveFields() {
        List<String> names = new ArrayList<>();
        collectComponentNames(PortfolioContext.class, names, new HashSet<>());
        assertTrue(names.size() > 5, "should have discovered the record-tree components");

        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String bad : FORBIDDEN) {
                assertFalse(lower.contains(bad),
                        "Corpus field '" + name + "' matches forbidden fragment '" + bad
                                + "' — sensitive data must not be in PortfolioContext (see Phase B4).");
            }
            assertNotEquals("id", lower, "raw entity id must not be exposed in the corpus");
            assertNotEquals("uuid", lower, "raw uuid must not be exposed in the corpus");
        }
    }

    /** Recursively gathers record-component names, descending into nested records and List<Record>. */
    private static void collectComponentNames(Class<?> type, List<String> out, Set<Class<?>> seen) {
        if (type == null || !type.isRecord() || !seen.add(type)) {
            return;
        }
        for (RecordComponent rc : type.getRecordComponents()) {
            out.add(rc.getName());
            Class<?> rcType = rc.getType();
            if (rcType.isRecord()) {
                collectComponentNames(rcType, out, seen);
            } else if (List.class.isAssignableFrom(rcType)
                    && rc.getGenericType() instanceof ParameterizedType pt) {
                Type arg = pt.getActualTypeArguments()[0];
                if (arg instanceof Class<?> argClass) {
                    collectComponentNames(argClass, out, seen);
                }
            }
        }
    }
}
