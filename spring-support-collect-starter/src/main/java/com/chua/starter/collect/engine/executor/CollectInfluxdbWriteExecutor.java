package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import com.chua.starter.collect.support.CollectJson;
import com.chua.starter.collect.support.CollectMiddlewareSupport;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.dto.InfluxdbWriteDTO;
import com.chua.starter.datasourcehub.support.service.InfluxdbManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * InfluxDB 写入节点：把来源集合按行协议写进 bucket / measurement。
 *
 * <p>复用数据源中心的 {@link InfluxdbManageService}（line protocol 提交），本节点只负责
 * 「行 → 行协议」的转换：列名按 tag / field 分流、类型按 line protocol 落字面量、
 * 时间戳列取数值字段，最后分块提交避免单请求体过大。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectInfluxdbWriteExecutor implements CollectShapeExecutor {

    /**
     * 数据源类型。
     */
    private static final String DS_TYPE = "influxdb";

    /**
     * 来源集合参数名。
     */
    private static final String SOURCE = "source";

    /**
     * 数据源参数名。
     */
    private static final String DATASOURCE = "datasource";

    /**
     * 目标 bucket 参数名。
     */
    private static final String BUCKET = "bucket";

    /**
     * measurement 参数名。
     */
    private static final String MEASUREMENT = "measurement";

    /**
     * tag 列参数名。
     */
    private static final String TAGS = "tags";

    /**
     * 时间戳列参数名。
     */
    private static final String TIMESTAMP = "timestampColumn";

    /**
     * 时间精度参数名。
     */
    private static final String PRECISION = "precision";

    /**
     * 每个请求最多提交的行数，控制单请求体大小。
     */
    private static final int CHUNK_SIZE = 1000;

    /**
     * 数据源中心 InfluxDB 服务。
     */
    private final ObjectProvider<InfluxdbManageService> influx;

    /**
     * 中间件公共支撑。
     */
    private final CollectMiddlewareSupport support;

    @Override
    public String supportShape() {
        return "influxdb-write";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "InfluxDB写入", "输出", "upload",
                "把来源集合逐行写进 InfluxDB（line protocol）；tag / field 按列名分流，无 schema 校验");
        meta.setOutputVariable("无出参；写入点数看节点摘要");
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.required(SOURCE, "来源集合", "textarea",
                        "填集合表达式，例 ${rows}；抽取、SQL、数据录入节点的出参可直接喂进来"),
                CollectShapeMetas.datasource(DATASOURCE, "InfluxDB 数据源", "",
                        "只接受 influxdb 类型数据源；应用主库 app 不适用"),
                CollectShapeMetas.required(BUCKET, "Bucket", "text",
                        "目标 bucket，可用 ${变量} 拼接；查询节点里通常用 ${bucket} 引用同一个值"),
                CollectShapeMetas.required(MEASUREMENT, "Measurement", "text",
                        "测量名，可用 ${变量} 拼接"),
                CollectShapeMetas.text(TAGS, "Tag 列", "text",
                        "逗号分隔哪些列作 tag（列名即 tag 键）；留空则除时间戳列外全部作 field"),
                CollectShapeMetas.text(TIMESTAMP, "时间戳列", "text",
                        "取行内的数值列作为时间戳；留空或非数值时由服务端按写入时间打点"),
                CollectShapeMetas.option(PRECISION, "时间精度", "s",
                        "「时间戳列」的单位", "ns", "us", "ms", "s"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) {
        List<Map<String, Object>> rows = support.sourceRows(node.param(SOURCE), variables);
        if (rows.isEmpty()) {
            variables.put("_written", 0);
            return;
        }
        InfluxdbManageService service = influx.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("InfluxDB 支持未启用（数据源中心未装载），无法写入");
        }
        DataSourceVO datasource = support.resolve(node.param(DATASOURCE), DS_TYPE);
        String bucket = CollectExpression.evalText(node.param(BUCKET), variables).trim();
        if (bucket.isEmpty()) {
            throw new IllegalStateException("未填写 Bucket");
        }
        String measurement = CollectExpression.evalText(node.param(MEASUREMENT), variables).trim();
        if (measurement.isEmpty()) {
            throw new IllegalStateException("未填写 Measurement");
        }
        String precision = CollectExpression.evalText(node.param(PRECISION), variables).trim();
        if (!"ns".equals(precision) && !"us".equals(precision) && !"ms".equals(precision) && !"s".equals(precision)) {
            precision = "s";
        }
        Set<String> tagColumns = splitColumns(CollectExpression.evalText(node.param(TAGS), variables));
        String timestampColumn = CollectExpression.evalText(node.param(TIMESTAMP), variables).trim();

        List<String> lines = new ArrayList<>(rows.size());
        int skipped = 0;
        for (Map<String, Object> row : rows) {
            String line = lineOf(measurement, row, tagColumns, timestampColumn);
            if (line == null) {
                skipped++;
            } else {
                lines.add(line);
            }
        }
        if (skipped > 0) {
            log.warn("[collect] InfluxDB 写入跳过 {} 行：没有任何可用 field 值", skipped);
        }
        int written = 0;
        for (int start = 0; start < lines.size(); start += CHUNK_SIZE) {
            List<String> chunk = lines.subList(start, Math.min(start + CHUNK_SIZE, lines.size()));
            service.write(new InfluxdbWriteDTO(bucket, precision, String.join("\n", chunk), datasource.id()));
            written += chunk.size();
        }
        variables.put("_written", written);
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        Object written = variables.get("_written");
        return "写入 " + (written instanceof Number ? ((Number) written).intValue() : 0) + " 点";
    }

    /**
     * 行 → line protocol。
     *
     * @param measurement     测量名
     * @param row             数据行
     * @param tagColumns      tag 列集合
     * @param timestampColumn 时间戳列
     * @return 行协议文本；没有任何可用 field 时返回 null（调用方计跳过）
     */
    private String lineOf(String measurement, Map<String, Object> row, Set<String> tagColumns,
                          String timestampColumn) {
        StringBuilder line = new StringBuilder(escape(measurement, " ,"));
        List<String> fields = new ArrayList<>();
        for (Map.Entry<String, Object> cell : row.entrySet()) {
            String key = cell.getKey();
            Object value = cell.getValue();
            if (value == null) {
                continue;
            }
            if (tagColumns.contains(key)) {
                line.append(',').append(escape(key, " =,")).append('=').append(escape(String.valueOf(value), " ,"));
                continue;
            }
            if (!timestampColumn.isEmpty() && timestampColumn.equals(key)) {
                continue;
            }
            String literal = fieldLiteral(value);
            if (literal != null) {
                fields.add(escape(key, " =,") + "=" + literal);
            }
        }
        if (fields.isEmpty()) {
            return null;
        }
        line.append(' ').append(String.join(",", fields));
        if (!timestampColumn.isEmpty()) {
            String timestamp = numericLiteral(row.get(timestampColumn));
            if (timestamp != null) {
                line.append(' ').append(timestamp);
            }
        }
        return line.toString();
    }

    /**
     * field 字面量。
     *
     * @param value 值
     * @return 行协议字面量；无法表达的值返回 null
     */
    private String fieldLiteral(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? "true" : "false";
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof BigInteger) {
            return value + "i";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Double || value instanceof Float) {
            return String.valueOf(value);
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        if (value instanceof CharSequence text) {
            return quote(text.toString());
        }
        if (value instanceof Map || value instanceof Iterable || value.getClass().isArray()) {
            return quote(CollectJson.toJson(value));
        }
        return quote(String.valueOf(value));
    }

    /**
     * 时间戳字面量：只收纯数字，其余交服务端打点。
     *
     * @param value 时间戳列的值
     * @return 数字文本，非数值返回 null
     */
    private String numericLiteral(Object value) {
        if (value instanceof Number number) {
            return number.toString();
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.matches("-?\\d+") ? text : null;
    }

    /**
     * 字符串 field 加双引号并转义。
     *
     * @param text 原文
     * @return 引号包裹的字面量
     */
    private String quote(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    /**
     * 行协议转义：需要转义的字符前加反斜杠。
     *
     * @param text  原文
     * @param extra 额外需要转义的字符（除反斜杠外）
     * @return 转义后文本
     */
    private String escape(String text, String extra) {
        StringBuilder builder = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\' || extra.indexOf(ch) >= 0) {
                builder.append('\\');
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    /**
     * 解析 tag 列清单。
     *
     * @param text 逗号分隔的列名
     * @return 列名集合，空行返回空集合
     */
    private Set<String> splitColumns(String text) {
        Set<String> columns = new HashSet<>();
        if (text == null || text.isBlank()) {
            return columns;
        }
        for (String part : text.split("[,，]")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                columns.add(name);
            }
        }
        return columns;
    }
}
