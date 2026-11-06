package com.chua.starter.collect.engine.executor;

import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import com.chua.starter.collect.support.CollectMiddlewareSupport;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.InfluxdbQueryResultVO;
import com.chua.starter.datasourcehub.support.model.dto.InfluxdbQueryDTO;
import com.chua.starter.datasourcehub.support.service.InfluxdbManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * InfluxDB 查询节点：执行 Flux，把结果行写进出参集合。
 *
 * <p>复用数据源中心 {@link InfluxdbManageService#query}（annotated CSV 解析、行数上限都在那一层），
 * 本节点只做「表达式展开 + 结果落作用域」。{@code bucket} 参数会注入求值作用域，
 * Flux 里直接写 {@code ${bucket}} 即可与参数保持一致。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectInfluxdbReadExecutor implements CollectShapeExecutor {

    /**
     * 数据源类型。
     */
    private static final String DS_TYPE = "influxdb";

    /**
     * 数据源参数名。
     */
    private static final String DATASOURCE = "datasource";

    /**
     * bucket 参数名（注入作用域供 {@code ${bucket}} 引用）。
     */
    private static final String BUCKET = "bucket";

    /**
     * Flux 参数名。
     */
    private static final String FLUX = "flux";

    /**
     * 出参集合变量名参数名。
     */
    private static final String OUTPUT = "outputVariable";

    /**
     * 出参集合默认名。
     */
    private static final String DEFAULT_OUTPUT = "rows";

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
        return "influxdb-read";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "InfluxDB查询", "来源", "search",
                "执行 Flux 查询 InfluxDB，结果行写进出参集合，下游按集合消费（同抽取节点 list 模式）");
        meta.setOutputVariable("默认 data，旧流程留空时按 ${rows} 兜底");
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.datasource(DATASOURCE, "InfluxDB 数据源", "",
                        "只接受 influxdb 类型数据源；应用主库 app 不适用"),
                CollectShapeMetas.text(BUCKET, "Bucket", "text",
                        "供 Flux 里 ${bucket} 引用；不使用可留空"),
                CollectShapeMetas.required(FLUX, "Flux 语句", "textarea",
                        "支持 ${} 表达式拼接；服务端返回行数有上限（超出截断，摘要会标注）"),
                CollectShapeMetas.outputVariable("查询结果写入的变量名，默认 data"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) {
        InfluxdbManageService service = influx.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("InfluxDB 支持未启用（数据源中心未装载），无法查询");
        }
        DataSourceVO datasource = support.resolve(node.param(DATASOURCE), DS_TYPE);
        // bucket 注入作用域：Flux 里写 ${bucket} 与参数栏保持同一来源
        Map<String, Object> scope = new LinkedHashMap<>(variables);
        scope.put(BUCKET, CollectExpression.evalText(node.param(BUCKET), variables).trim());
        String flux = CollectExpression.evalText(node.param(FLUX), scope).trim();
        if (flux.isEmpty()) {
            throw new IllegalStateException("未填写 Flux 语句");
        }
        InfluxdbQueryResultVO result = service.query(new InfluxdbQueryDTO(flux, datasource.id(), null));
        List<Map<String, Object>> rows = new ArrayList<>(result.rows());
        variables.put(outputOf(node), rows);
        if (Boolean.TRUE.equals(result.truncated())) {
            log.info("[collect] InfluxDB 查询被行数上限截断：返回 {} 行（服务端共 {} 行）",
                    result.rowCount(), result.totalRows());
        }
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        return "查询 " + support.sizeOf(variables, outputOf(node)) + " 行";
    }

    @Override
    public String defaultOutput(CollectNode node) {
        return DEFAULT_OUTPUT;
    }

    /**
     * 解析出参变量名，空值回落默认名。
     *
     * @param node 节点
     * @return 出参变量名
     */
    private String outputOf(CollectNode node) {
        String output = node.param(OUTPUT).trim();
        return output.isEmpty() ? DEFAULT_OUTPUT : output;
    }
}
