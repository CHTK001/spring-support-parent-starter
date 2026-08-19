package com.chua.starter.datasync.dbprobe;

import com.chua.common.support.base.serialize.Serialization;
import com.chua.common.support.lang.json.JacksonJsonProvider;
import com.chua.common.support.lang.json.JsonProvider;
import com.chua.common.support.serialize.Serializer;
import com.chua.fory.support.serialize.ForySerialization;
import com.chua.protobuf.support.serialize.ProtobufSerialization;
import com.chua.serialize.support.kryo.KryoSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 序列化实现基准测试 — 使用 datasync 实际数据格式 {@code List<Map<String, Object>>}。
 *
 * @since 4.0.0.42
 */
public final class DataSyncSerialBenchProbe {

    static final int ROWS = 1000;
    static final int WARMUP_MS = 500;
    static final int MEASURE_MS = 1000;
    static final int ROUNDS = 3;
    static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        // 构造 datasync 实际数据：1000 行，每行 6 个字段
        List<Map<String, Object>> data = new ArrayList<>(ROWS);
        for (int i = 0; i < ROWS; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", i);
            row.put("name", "user_" + i);
            row.put("age", 20 + i % 40);
            row.put("score", (i * 7) % 100 + 1.0);
            row.put("active", i % 2 == 0);
            row.put("created_at", "2026-01-01");
            data.add(row);
        }

        System.err.println("========================================================================");
        System.err.println("序列化实现基准 — List<Map> × " + ROWS + " 行 (6 字段)");
        System.err.println("========================================================================\n");
        System.err.printf("%-16s %-8s %-12s %-12s %-12s %-16s%n",
                "实现", "大小(B)", "序列化(ops/s)", "反序列化(ops/s)", "平均(ops/s)", "往返");
        System.err.println("------------------------------------------------------------------------");

        // 1. Jackson (当前实现)
        benchJackson(data);
        // 2. JacksonJsonProvider (通用 JSON 门面)
        benchJsonProvider("jackson-json", new JacksonJsonProvider(), data);
        // 3. Fory (Apache Fury 二进制)
        try { benchBinary("fury", new ForySerialization(), data); } catch (Throwable t) { System.err.println("fury: " + t.getMessage()); }
        // 4. Protobuf (protostuff)
        try { benchBinary("protobuf", new ProtobufSerialization(), data); } catch (Throwable t) { System.err.println("protobuf: " + t.getMessage()); }
        // 5. Kryo
        try { benchKryo(data); } catch (Throwable t) { System.err.println("kryo: " + t.getMessage()); }

        System.err.println("========================================================================");
    }

    static void benchJackson(List<Map<String, Object>> data) throws Exception {
        String json = MAPPER.writeValueAsString(data);
        long size = json.getBytes("UTF-8").length;
        boolean ok = data.equals(MAPPER.readValue(json, List.class));
        // 验证：反序列化后应得到 ArrayList of LinkedHashMap
        Object back = MAPPER.readValue(json, List.class);
        ok &= back instanceof List;
        if (ok) {
            List<?> list = (List<?>) back;
            ok &= !list.isEmpty() && list.get(0) instanceof Map;
        }

        Runnable ser = () -> { try { MAPPER.writeValueAsString(data); } catch (Exception e) { throw new RuntimeException(e); } };
        Runnable des = () -> { try { MAPPER.readValue(json, List.class); } catch (Exception e) { throw new RuntimeException(e); } };
        report("jackson(当前)", size, ser, des, ok);
    }

    @SuppressWarnings("unchecked")
    static void benchBinary(String label, Serialization s, List<Map<String, Object>> data) throws Exception {
        byte[] bytes = s.serialize(data);
        long size = bytes.length;
        Object back = s.deserialize(bytes, List.class);
        boolean ok = data.equals(back);

        Runnable ser = () -> { try { s.serialize(data); } catch (Exception e) { throw new RuntimeException(e); } };
        Runnable des = () -> { try { s.deserialize(bytes, List.class); } catch (Exception e) { throw new RuntimeException(e); } };
        report(label, size, ser, des, ok);
    }

    @SuppressWarnings("unchecked")
    static void benchJsonProvider(String label, JsonProvider p, List<Map<String, Object>> data) throws Exception {
        String json = p.toJson(data);
        long size = json.getBytes("UTF-8").length;
        Object back = p.fromJson(json, List.class);
        boolean ok = data.equals(back);

        Runnable ser = () -> { try { p.toJson(data); } catch (Exception e) { throw new RuntimeException(e); } };
        Runnable des = () -> { try { p.fromJson(json, List.class); } catch (Exception e) { throw new RuntimeException(e); } };
        report(label, size, ser, des, ok);
    }

    @SuppressWarnings("unchecked")
    static void benchKryo(List<Map<String, Object>> data) throws Exception {
        KryoSerializer<Serializable> ks = new KryoSerializer<>((Class<Serializable>)(Class<?>)List.class);
        byte[] bytes = ks.serialize((Serializable) data);
        long size = bytes.length;
        Object back = ks.deserialize(bytes);
        boolean ok = data.equals(back);

        Runnable ser = () -> { try { ks.serialize((Serializable) data); } catch (Exception e) { throw new RuntimeException(e); } };
        Runnable des = () -> { try { ks.deserialize(bytes); } catch (Exception e) { throw new RuntimeException(e); } };
        report("kryo", size, ser, des, ok);
    }

    static void report(String label, long size, Runnable ser, Runnable des, boolean ok) {
        warmup(ser, WARMUP_MS);
        warmup(des, WARMUP_MS);
        long serOps = bestOf(ser, MEASURE_MS, ROUNDS);
        long desOps = bestOf(des, MEASURE_MS, ROUNDS);
        long avg = (serOps + desOps) / 2;
        System.err.printf("%-16s %,8d %,12d %,12d %,12d %s%n",
                label, size, serOps, desOps, avg, ok ? "[OK]" : "[FAIL]");
    }

    static void warmup(Runnable task, long ms) {
        long deadline = System.nanoTime() + ms * 1_000_000L;
        while (System.nanoTime() < deadline) task.run();
    }

    static long measureOps(Runnable task, long ms) {
        long start = System.nanoTime();
        long deadline = start + ms * 1_000_000L;
        long count = 0;
        while (System.nanoTime() < deadline) { task.run(); count++; }
        return count * 1_000_000_000L / (System.nanoTime() - start);
    }

    static long bestOf(Runnable task, long ms, int rounds) {
        long best = 0;
        for (int i = 0; i < rounds; i++) best = Math.max(best, measureOps(task, ms));
        return best;
    }
}