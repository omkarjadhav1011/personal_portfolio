package com.portfolio.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Raw-SQL access to the {@code embedding} vector store (Phase C1). Uses {@link JdbcTemplate}
 * because JPA/Hibernate doesn't understand pgvector's {@code vector} type — embeddings are bound
 * as the pgvector text literal {@code [v1,v2,...]} and cast with {@code ?::vector}.
 *
 * <p>Keyed on {@code (source_type, source_id)} so re-indexing a changed source replaces its row
 * instead of duplicating (upsert), and a removed source can be pruned (Phase D).
 */
@Repository
public class EmbeddingRepository {

    private final JdbcTemplate jdbc;

    public EmbeddingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A nearest-neighbour hit: the chunk text plus its cosine distance (smaller = closer). */
    public record Neighbor(String sourceType, String sourceId, String chunkText, double distance) {
    }

    /** Inserts or replaces the embedding for one chunk. */
    public void upsert(String sourceType, String sourceId, String chunkText, float[] embedding) {
        jdbc.update("""
                INSERT INTO embedding (source_type, source_id, chunk_text, embedding, updated_at)
                VALUES (?, ?, ?, ?::vector, now())
                ON CONFLICT (source_type, source_id) DO UPDATE
                    SET chunk_text = EXCLUDED.chunk_text,
                        embedding  = EXCLUDED.embedding,
                        updated_at = now()
                """, sourceType, sourceId, chunkText, toVectorLiteral(embedding));
    }

    /** Removes one chunk by its source key. Returns rows deleted. */
    public int deleteBySource(String sourceType, String sourceId) {
        return jdbc.update("DELETE FROM embedding WHERE source_type = ? AND source_id = ?",
                sourceType, sourceId);
    }

    /** Removes every chunk of a type. Returns rows deleted. */
    public int deleteByType(String sourceType) {
        return jdbc.update("DELETE FROM embedding WHERE source_type = ?", sourceType);
    }

    /**
     * Prunes chunks of a type whose source_id is no longer present (e.g. a project was deleted).
     * Returns rows deleted. An empty keep-set deletes all rows of the type.
     */
    public int deleteByTypeNotIn(String sourceType, Collection<String> keepSourceIds) {
        if (keepSourceIds.isEmpty()) {
            return deleteByType(sourceType);
        }
        String placeholders = String.join(",", keepSourceIds.stream().map(id -> "?").toList());
        Object[] args = new Object[keepSourceIds.size() + 1];
        args[0] = sourceType;
        int i = 1;
        for (String id : keepSourceIds) {
            args[i++] = id;
        }
        return jdbc.update(
                "DELETE FROM embedding WHERE source_type = ? AND source_id NOT IN (" + placeholders + ")",
                args);
    }

    /** Top-k chunks nearest the query embedding by cosine distance. */
    public List<Neighbor> findNearest(float[] queryEmbedding, int k) {
        String vec = toVectorLiteral(queryEmbedding);
        return jdbc.query("""
                SELECT source_type, source_id, chunk_text, (embedding <=> ?::vector) AS distance
                FROM embedding
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, rowNum) -> new Neighbor(
                        rs.getString("source_type"),
                        rs.getString("source_id"),
                        rs.getString("chunk_text"),
                        rs.getDouble("distance")),
                vec, vec, k);
    }

    /** Total chunks indexed. */
    public long count() {
        Long c = jdbc.queryForObject("SELECT count(*) FROM embedding", Long.class);
        return c == null ? 0L : c;
    }

    /** Formats a float[] as the pgvector text literal {@code [v1,v2,...]}. */
    static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8 + 2).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
