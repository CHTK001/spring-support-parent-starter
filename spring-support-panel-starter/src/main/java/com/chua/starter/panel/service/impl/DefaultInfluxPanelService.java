package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.service.InfluxPanelService;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.domain.Bucket;
import com.influxdb.client.domain.HealthCheck;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 InfluxDB 2.x 面板服务实现。
 *
 * @author CH
 */
public class DefaultInfluxPanelService implements InfluxPanelService {

    private record InfluxConnection(InfluxDBClient client, String org, String bucket) {}

    private final Map<String, InfluxConnection> connectionMap = new ConcurrentHashMap<>();

    @Override
    public String open(String host, int port, String token, String org, String bucket) {
        String url = "http://" + host + ":" + port;
        InfluxDBClient client = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
        // 验证连接
        HealthCheck health = client.health();
        if (!"pass".equals(health.getStatus().getValue())) {
            client.close();
            throw new RuntimeException("InfluxDB 连接失败: " + health.getMessage());
        }
        String connectionId = UUID.randomUUID().toString();
        connectionMap.put(connectionId, new InfluxConnection(client, org, bucket));
        return connectionId;
    }

    @Override
    public void close(String connectionId) {
        InfluxConnection conn = connectionMap.remove(connectionId);
        if (conn != null) {
            try {
                conn.client().close();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public List<String> listBuckets(String connectionId) {
        InfluxConnection conn = getConn(connectionId);
        List<Bucket> buckets = conn.client().getBucketsApi().findBuckets();
        List<String> names = new ArrayList<>();
        if (buckets != null) {
            for (Bucket b : buckets) {
                names.add(b.getName());
            }
        }
        return names;
    }

    @Override
    public List<String> listMeasurements(String connectionId, String bucket) {
        InfluxConnection conn = getConn(connectionId);
        String flux = "import \"influxdata/influxdb/schema\"\n"
                + "schema.measurements(bucket: \"" + bucket + "\")";
        List<FluxTable> tables = conn.client().getQueryApi().query(flux, conn.org());
        List<String> measurements = new ArrayList<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Object val = record.getValue();
                if (val != null) {
                    measurements.add(val.toString());
                }
            }
        }
        return measurements;
    }

    @Override
    public List<Map<String, Object>> query(String connectionId, String fluxQuery) {
        InfluxConnection conn = getConn(connectionId);
        List<FluxTable> tables = conn.client().getQueryApi().query(fluxQuery, conn.org());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("_time", record.getTime() != null ? record.getTime().toString() : null);
                row.put("_measurement", record.getMeasurement());
                row.put("_field", record.getField());
                row.put("_value", record.getValue());
                // 附加 tags
                record.getValues().forEach((k, v) -> {
                    if (!k.startsWith("_") && v != null) {
                        row.put(k, v);
                    }
                });
                rows.add(row);
            }
        }
        return rows;
    }

    @Override
    public void write(String connectionId, String bucket, String lineProtocol) {
        InfluxConnection conn = getConn(connectionId);
        conn.client().getWriteApiBlocking()
                .writeRecord(bucket, conn.org(), WritePrecision.NS, lineProtocol);
    }

    @Override
    public Map<String, Object> health(String connectionId) {
        InfluxConnection conn = getConn(connectionId);
        HealthCheck health = conn.client().health();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", health.getStatus() != null ? health.getStatus().getValue() : "unknown");
        result.put("message", health.getMessage());
        result.put("name", health.getName());
        result.put("version", health.getVersion());
        result.put("commit", health.getCommit());
        return result;
    }

    // ─── 内部工具 ──────────────────────────────────────────────────────────────

    private InfluxConnection getConn(String connectionId) {
        InfluxConnection conn = connectionMap.get(connectionId);
        if (conn == null) {
            throw new IllegalStateException("InfluxDB 连接不存在: " + connectionId);
        }
        return conn;
    }
}
