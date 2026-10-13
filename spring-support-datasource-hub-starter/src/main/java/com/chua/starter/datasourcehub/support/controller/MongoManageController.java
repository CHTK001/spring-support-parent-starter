package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.MongoCollectionVO;
import com.chua.starter.datasourcehub.support.model.MongoDatabaseVO;
import com.chua.starter.datasourcehub.support.model.MongoQueryResultVO;
import com.chua.starter.datasourcehub.support.model.MongoTestVO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datasourcehub.support.model.dto.MongoDocumentDTO;
import com.chua.starter.datasourcehub.support.model.dto.MongoQueryDTO;
import com.chua.starter.datasourcehub.support.service.MongoManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
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
 * MongoDB 管理 REST 接口。
 *
 * <p>四个接口的 {@code datasourceId} 均可缺省，由服务端退化为第一个 mongodb 数据源；
 * 数据源行的 {@code port} 填 MongoDB 服务端口（默认 27017），{@code database}
 * 为默认逻辑库兼认证库，{@code username}/{@code password} 为凭据。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控管理", description = "MongoDB 管理")
@RestController
@RequestMapping("/ms/monitor/mongodb")
@RequiredArgsConstructor
public class MongoManageController {

    /**
     * MongoDB 管理服务
     */
    private final MongoManageService mongoManageService;

    /**
     * 测试 MongoDB 连通性。
     *
     * @param request 测试请求，可为空
     * @return 测试结果
     */
    @Operation(summary = "测试 MongoDB 连接")
    @PostMapping("/test")
    public ReturnResult<MongoTestVO> test(@RequestBody(required = false) MiddlewareTestDTO request) {
        return call("测试 MongoDB 连接", () -> mongoManageService.test(request));
    }

    /**
     * 查询逻辑库列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 逻辑库列表
     */
    @Operation(summary = "MongoDB 逻辑库列表")
    @GetMapping("/databases")
    public ReturnResult<List<MongoDatabaseVO>> databases(@RequestParam(required = false) Long datasourceId) {
        return call("读取 MongoDB 逻辑库", () -> mongoManageService.databases(datasourceId));
    }

    /**
     * 查询集合列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @param database     逻辑库名，为空用数据源默认库
     * @return 集合列表
     */
    @Operation(summary = "MongoDB 集合列表")
    @GetMapping("/collections")
    public ReturnResult<List<MongoCollectionVO>> collections(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        return call("读取 MongoDB 集合", () -> mongoManageService.collections(datasourceId, database));
    }

    /**
     * 执行只读 shell 查询。
     *
     * @param request 查询请求
     * @return 查询结果
     */
    @Operation(summary = "执行 MongoDB 只读查询")
    @PostMapping("/query")
    public ReturnResult<MongoQueryResultVO> query(@RequestBody MongoQueryDTO request) {
        return call("执行 MongoDB 查询", () -> mongoManageService.query(request));
    }

    /**
     * 按 ID 查看单个文档。
     *
     * @param request 查看请求
     * @return 文档内容
     */
    @Operation(summary = "查看 MongoDB 单个文档")
    @PostMapping("/document")
    public ReturnResult<Map<String, Object>> document(@RequestBody MongoDocumentDTO request) {
        return call("读取 MongoDB 文档", () -> mongoManageService.document(request));
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
            log.error("[mongodb] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }
}
