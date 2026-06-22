package com.portfolio.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeminiEmbeddingClientTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void parsesSingleEmbedContentResponse() {
        String json = "{\"embedding\":{\"values\":[0.1,-0.2,0.3]}}";
        float[] vec = GeminiEmbeddingClient.parseSingle(om, json);
        assertArrayEquals(new float[]{0.1f, -0.2f, 0.3f}, vec, 1e-6f);
    }

    @Test
    void parsesBatchEmbedContentsResponse() {
        String json = "{\"embeddings\":[{\"values\":[1.0,2.0]},{\"values\":[3.0,4.0]}]}";
        List<float[]> vecs = GeminiEmbeddingClient.parseBatch(om, json);
        assertEquals(2, vecs.size());
        assertArrayEquals(new float[]{1.0f, 2.0f}, vecs.get(0), 1e-6f);
        assertArrayEquals(new float[]{3.0f, 4.0f}, vecs.get(1), 1e-6f);
    }

    @Test
    void throwsOnMissingValues() {
        assertThrows(IllegalStateException.class,
                () -> GeminiEmbeddingClient.parseSingle(om, "{\"error\":{\"code\":429}}"));
    }

    @Test
    void vectorLiteralFormatsForPgvector() {
        assertEquals("[1.0,2.5,-3.0]", EmbeddingRepository.toVectorLiteral(new float[]{1.0f, 2.5f, -3.0f}));
    }
}
