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
import com.chua.starter.datasourcehub.support.model.Neo4jQueryResultVO;
import com.chua.starter.datasourcehub.support.model.dto.Neo4jQueryDTO;
import com.chua.starter.datasourcehub.support.service.Neo4jManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Neo4j 查询节点：执行 Cypher，把结果行写进出参集合。
 *
 * <p>复用数据源中心 {@link Neo4jManageService#query}（行数上限在那一层），
 * 本节点只做「表达式展开 + 结果落作用域」，与 InfluxDB 查询节点同一套语义。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectNeo4jReadExecutor implements CollectShapeExecutor {

    /**
     * 数据源类型。
     */
    private static final String DS_TYPE = "neo4j";

    /**
     * 数据源参数名。
     */
    private static final String DATASOURCE = "datasource";

    /**
     * Cypher 参数名。
     */
    private static final String CYPHER = "cypher";

    /**
     * 出参集合变量名参数名。
     */
    private static final String OUTPUT = "outputVariable";

    /**
     * 出参集合默认名。
     */
    private static final String DEFAULT_OUTPUT = "rows";

    /**
     * 数据源中心 Neo4j 服务。
     */
    private final ObjectProvider<Neo4jManageService> neo4j;

    /**
     * 中间件公共支撑。
     */
    private final CollectMiddlewareSupport support;

    @Override
    public String supportShape() {
        return "neo4j-read";
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "Neo4j查询", "来源", "share",
                "执行 Cypher 查询 Neo4j，结果行写进出参集合，下游按集合消费（同抽取节点 list 模式）");
        meta.setOutputVariable("默认 data，旧流程留空时按 ${rows} 兜底");
        return CollectShapeMetas.with(meta,
                CollectShapeMetas.datasource(DATASOURCE, "Neo4j 数据源", "",
                        "只接受 neo4j 类型数据源；应用主库 app 不适用"),
                CollectShapeMetas.required(CYPHER, "Cypher 语句", "textarea",
                        "支持 ${} 表达式拼接，例 MATCH (n:Person) RETURN n LIMIT ${limit}；服务端返回行数有上限（超出截断）"),
                CollectShapeMetas.outputVariable("查询结果写入的变量名，默认 data"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) {
        Neo4jManageService service = neo4j.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Neo4j 支持未启用（数据源中心未装载），无法查询");
        }
        DataSourceVO datasource = support.resolve(node.param(DATASOURCE), DS_TYPE);
        String cypher = CollectExpression.evalText(node.param(CYPHER), variables).trim();
        if (cypher.isEmpty()) {
            throw new IllegalStateException("未填写 Cypher 语句");
        }
        Neo4jQueryResultVO result = service.query(new Neo4jQueryDTO(cypher, datasource.id()));
        List<Map<String, Object>> rows = new ArrayList<>(result.rows());
        variables.put(outputOf(node), rows);
        if (Boolean.TRUE.equals(result.truncated())) {
            log.info("[collect] Neo4j 查询被行数上限截断：返回 {} 行（服务端共 {} 行）",
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
