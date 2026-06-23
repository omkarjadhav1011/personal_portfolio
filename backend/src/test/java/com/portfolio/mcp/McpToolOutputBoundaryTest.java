package com.portfolio.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
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
 * Phase B2 — public-data-only by construction for the MCP tool surface. Reflects over every
 * {@code @Tool} method on {@link PortfolioMcpTools} and asserts its output type is a curated public
 * record (never a JPA entity or raw {@code byte[]}) whose field-name tree contains nothing
 * sensitive. New tools added in Phase Groups C/D are covered automatically — a leaky return type
 * fails the build, rather than relying on each tool behaving. Complements
 * {@code chatbot.CorpusBoundaryTest} (the corpus shape) and
 * {@code query.PortfolioQueryServiceTest} (the facade), one layer out at the tool boundary.
 */
class McpToolOutputBoundaryTest {

    /** Lowercased field-name fragments that must NEVER appear anywhere in a tool's output shape. */
    private static final List<String> FORBIDDEN = List.of(
            "password", "secret", "hash", "apikey", "token", "credential",
            "avatardata", "resumedata", "rawbytes", "bytes",
            "createdat", "updatedat", "deletedat", "internal");

    @Test
    void everyToolReturnsOnlyCuratedPublicData() {
        List<Method> tools = new ArrayList<>();
        for (Method m : PortfolioMcpTools.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Tool.class)) {
                tools.add(m);
            }
        }
        assertTrue(!tools.isEmpty(), "expected at least one @Tool method on PortfolioMcpTools");

        for (Method tool : tools) {
            Class<?> outputType = unwrapList(tool.getGenericReturnType());
            assertTrue(outputType.isRecord(),
                    "@Tool '" + tool.getName() + "' must return a curated public record (or a List of "
                            + "one), never a JPA entity or raw byte[]; was " + outputType.getName());

            List<String> names = new ArrayList<>();
            collectComponentNames(outputType, names, new HashSet<>());
            for (String name : names) {
                String lower = name.toLowerCase(Locale.ROOT);
                for (String bad : FORBIDDEN) {
                    assertFalse(lower.contains(bad),
                            "@Tool '" + tool.getName() + "' output field '" + name
                                    + "' matches forbidden fragment '" + bad + "'.");
                }
                assertNotEquals("id", lower, "raw entity id must not be exposed by a tool output");
                assertNotEquals("uuid", lower, "raw uuid must not be exposed by a tool output");
            }
        }
    }

    /** Unwraps {@code List<X>} to {@code X}; returns the raw class otherwise. */
    private static Class<?> unwrapList(Type type) {
        if (type instanceof ParameterizedType pt
                && pt.getRawType() instanceof Class<?> raw && List.class.isAssignableFrom(raw)) {
            Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> c) {
                return c;
            }
        }
        return type instanceof Class<?> c ? c : Object.class;
    }

    /** Recursively gathers record-component names, descending into nested records and List&lt;Record&gt;. */
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
