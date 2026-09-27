package com.chua.starter.datasourcehub.support.controller;

import com.chua.starter.datasourcehub.support.model.DataSourceTestVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Neo4j 图数据库数据源专属能力。
 *
 * <p>台账 CRUD 与通用连通性测试见 {@link DatasourceController}；本控制器只补
 * Neo4j 图数据库 特有的运行时信息探测，并复用统一的测速与落库逻辑。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据源中心 - Neo4j 图数据库", description = "Neo4j 图数据库 数据源专属能力")
@RestController
@RequestMapping("/ms/datasource/neo4j")
public class Neo4jDatasourceController extends DatasourceTypeController {

    /**
     * 构造方法
     *
     * @param datasourceService 数据源服务
     */
    public Neo4jDatasourceController(DatasourceService datasourceService) {
        super(datasourceService);
    }

    @Override
    protected String type() {
        return "neo4j";
    }

    /**
     * 测试 Neo4j 图数据库 数据源连通性。
     *
     * @param id 数据源 ID
     * @return 测试结果（含连接延迟与会话延迟）
     */
    @Operation(summary = "测试 Neo4j 图数据库 数据源连接")
    @PostMapping("/{id}/test")
    public ReturnResult<DataSourceTestVO> test(@PathVariable Long id) {
        return super.test(id);
    }

    /**
     * 读取 Neo4j 图数据库 数据源运行时信息。
     *
     * @param id 数据源 ID
     * @return 键值信息
     */
    @Operation(summary = "读取 Neo4j 图数据库 数据源运行时信息")
    @GetMapping("/{id}/info")
    public ReturnResult<Map<String, String>> info(@PathVariable Long id) {
        return super.info(id);
    }
}
