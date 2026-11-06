package com.example.oaticket.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * 工单向量存取
 *
 * 向量以 BLOB 存：float[] 每个元素 4 字节，1024 维就是 4096 字节。
 * 存成 JSON 数组字符串会膨胀五倍以上，没必要
 */
@Repository
public class TicketEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public TicketEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int count() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_embedding", Integer.class);
        return n == null ? 0 : n;
    }

    public void save(String ticketNo, String text, float[] vector) {
        jdbcTemplate.update(
                "INSERT OR REPLACE INTO ticket_embedding (ticket_no, text, embedding) VALUES (?,?,?)",
                ticketNo, text, toBytes(vector));
    }

    /**
     * 全部向量一次性读出来
     *
     * 不做分页：工单量在千条以内时，全量读进内存算余弦比任何数据库方案都快，
     * 上向量库属于过度设计。量级到十万条以上再换方案
     *
     * @return 全部已建索引的工单向量
     */
    public List<StoredVector> findAll() {
        return jdbcTemplate.query(
                "SELECT ticket_no, embedding FROM ticket_embedding",
                (rs, rowNum) -> new StoredVector(
                        rs.getString("ticket_no"),
                        fromBytes(rs.getBytes("embedding"))));
    }

    private byte[] toBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    private float[] fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return new float[0];
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] vector = new float[bytes.length / 4];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat(i * 4);
        }
        return vector;
    }

    /**
     * 库里读出的一条向量
     */
    public record StoredVector(String ticketNo, float[] vector) {
    }
}
