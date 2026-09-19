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
 * @author CH
 */
public final class DataSyncSerialBenchProbe {

    static final int ROWS = 1000; // ROWS
    static final int WARMUP_MS = 500; // WARMUP_MS
    static final int MEASURE_MS = 1000; // 测量ms
    static final int ROUNDS = 3; // ROUNDS
    static final ObjectMapper MAPPER = new ObjectMapper(); // 映射器

    /**
     * Main
     *
     * @param args 参数
     */
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
        try {
            benchBinary("fury", new ForySerialization(), data);
        } catch (Throwable t) {
            System.err.println("fury: " + t.getMessage());
        }
        // 4. Protobuf (protostuff)
        try {
            benchBinary("protobuf", new ProtobufSerialization(), data);
        } catch (Throwable t) {
            System.err.println("protobuf: " + t.getMessage());
        }
        // 5. Kryo
        try {
            benchKryo(data);
        } catch (Throwable t) {
            System.err.println("kryo: " + t.getMessage());
        }

        System.err.println("========================================================================");
    }

    /**
     * benchjackson
     *
     * @param data 数据
     */
    static void benchJackson(List<Map<String, Object>> data) throws Exception {
        String json = MAPPER.writeValueAsString(data);
        long size = json.getBytes("UTF-8").length;
        boolean ok = data.equals(MAPPER.readValue(json, List.class));
 // 验证：反序列化后应得到 array列表 的 链接哈希映射
        Object back = MAPPER.readValue(json, List.class);
        ok &= back instanceof List;
        if (ok) {
            List<?> list = (List<?>) back;
            ok &= !list.isEmpty() && list.getFirst() instanceof Map;
        }

        Runnable ser = () -> {
            try {
                MAPPER.writeValueAsString(data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        Runnable des = () -> {
            try {
                MAPPER.readValue(json, List.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        report("jackson(当前)", size, ser, des, ok);
    }

    @SuppressWarnings("unchecked")
    /**
     * benchbinary
     *
     * @param label 标签
     * @param s s
     * @param data 数据
     */
    static void benchBinary(String label, Serialization s, List<Map<String, Object>> data) throws Exception {
        byte[] bytes = s.serialize(data);
        long size = bytes.length;
        Object back = s.deserialize(bytes, List.class);
        boolean ok = data.equals(back);

        Runnable ser = () -> {
            try {
                s.serialize(data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        Runnable des = () -> {
            try {
                s.deserialize(bytes, List.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        report(label, size, ser, des, ok);
    }

    @SuppressWarnings("unchecked")
    /**
     * benchjson提供者
     *
     * @param label 标签
     * @param p p
     * @param data 数据
     */
    static void benchJsonProvider(String label, JsonProvider p, List<Map<String, Object>> data) throws Exception {
        String json = p.toJson(data);
        long size = json.getBytes("UTF-8").length;
        Object back = p.fromJson(json, List.class);
        boolean ok = data.equals(back);

        Runnable ser = () -> {
            try {
                p.toJson(data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        Runnable des = () -> {
            try {
                p.fromJson(json, List.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        report(label, size, ser, des, ok);
    }

    @SuppressWarnings("unchecked")
    /**
     * benchkryo
     *
     * @param data 数据
     */
    static void benchKryo(List<Map<String, Object>> data) throws Exception {
        KryoSerializer<Serializable> ks = new KryoSerializer<>((Class<Serializable>)(Class<?>)List.class);
        byte[] bytes = ks.serialize((Serializable) data);
        long size = bytes.length;
        Object back = ks.deserialize(bytes);
        boolean ok = data.equals(back);

        Runnable ser = () -> {
            try {
                ks.serialize((Serializable) data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        Runnable des = () -> {
            try {
                ks.deserialize(bytes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        report("kryo", size, ser, des, ok);
    }

    /**
     * Report
     *
     * @param label 标签
     * @param size 大小
     * @param ser ser
     * @param des des
     * @param ok ok
     */
    static void report(String label, long size, Runnable ser, Runnable des, boolean ok) {
        warmup(ser, WARMUP_MS);
        warmup(des, WARMUP_MS);
        long serOps = bestOf(ser, MEASURE_MS, ROUNDS);
        long desOps = bestOf(des, MEASURE_MS, ROUNDS);
        long avg = (serOps + desOps) / 2;
        System.err.printf("%-16s %,8d %,12d %,12d %,12d %s%n",
                label, size, serOps, desOps, avg, ok ? "[OK]" : "[FAIL]");
    }

    /**
     * Warmup
     *
     * @param task 任务
     * @param ms ms
     */
    static void warmup(Runnable task, long ms) {
        long deadline = System.nanoTime() + ms * 1_000_000L;
        while (System.nanoTime() < deadline) {
            task.run();
        }
    }

    /**
     * 测量ops
     *
     * @param task 任务
     * @param ms ms
     * @return 测量ops的结果
     */
    static long measureOps(Runnable task, long ms) {
        long start = System.nanoTime();
        long deadline = start + ms * 1_000_000L;
        long count = 0;
        while (System.nanoTime() < deadline) {
            task.run();
            count++;
        }
        return count * 1_000_000_000L / (System.nanoTime() - start);
    }

    /**
     * best的
     *
     * @param task 任务
     * @param ms ms
     * @param rounds rounds
     * @return best的的结果
     */
    static long bestOf(Runnable task, long ms, int rounds) {
        long best = 0;
        for (int i = 0; i < rounds; i++) {
            best = Math.max(best, measureOps(task, ms));
        }
        return best;
    }
}