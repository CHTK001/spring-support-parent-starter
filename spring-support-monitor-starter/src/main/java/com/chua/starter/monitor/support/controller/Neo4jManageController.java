package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.Neo4jLabelVO;
import com.chua.starter.monitor.support.model.Neo4jQueryResultVO;
import com.chua.starter.monitor.support.model.Neo4jRelationshipVO;
import com.chua.starter.monitor.support.model.Neo4jTestVO;
import com.chua.starter.monitor.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.monitor.support.model.dto.Neo4jQueryDTO;
import com.chua.starter.monitor.support.service.Neo4jManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Neo4j 图数据库管理 REST 接口。
 *
 * <p>四个接口的 {@code datasourceId} 均可缺省，由服务端退化为第一个 neo4j 数据源；
 * 数据源行的端口填 Neo4j 的 HTTP 端口（默认 7474），管理侧不走 Bolt。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控管理", description = "Neo4j 图数据库管理")
@RestController
@RequestMapping("/ms/monitor/neo4j")
@RequiredArgsConstructor
public class Neo4jManageController {

    /**
     * Neo4j 管理服务
     */
    private final Neo4jManageService neo4jManageService;

    /**
     * 测试 Neo4j 连通性。
     *
     * @param request 测试请求，可为空
     * @return 测试结果
     */
    @Operation(summary = "测试 Neo4j 连接")
    @PostMapping("/test")
    public ReturnResult<Neo4jTestVO> test(@RequestBody(required = false) MiddlewareTestDTO request) {
        return call("测试 Neo4j 连接", () -> neo4jManageService.test(request));
    }

    /**
     * 查询标签列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 标签统计行
     */
    @Operation(summary = "Neo4j 标签列表")
    @GetMapping("/labels")
    public ReturnResult<List<Neo4jLabelVO>> labels(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Neo4j 标签", () -> neo4jManageService.labels(datasourceId));
    }

    /**
     * 查询关系类型列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 关系类型统计行
     */
    @Operation(summary = "Neo4j 关系类型列表")
    @GetMapping("/relationships")
    public ReturnResult<List<Neo4jRelationshipVO>> relationships(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Neo4j 关系类型", () -> neo4jManageService.relationships(datasourceId));
    }

    /**
     * 执行 Cypher 查询。
     *
     * @param request 查询请求
     * @return 查询结果
     */
    @Operation(summary = "执行 Neo4j Cypher 查询")
    @PostMapping("/query")
    public ReturnResult<Neo4jQueryResultVO> query(@RequestBody Neo4jQueryDTO request) {
        return call("执行 Neo4j 查询", () -> neo4jManageService.query(request));
    }

    /**
     * 执行一次管理操作，把远端失败换成可读原因而不是 500 堆栈。
     *
     * @param action   操作描述，用于日志
     * @param supplier 业务调用
     * @param <T>      返回类型
     * @return 响应
     */
    private <T> ReturnResult<T> call(String action, Supplier<T> supplier) {
        try {
            return ReturnResult.ok(supplier.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[neo4j] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }
}
