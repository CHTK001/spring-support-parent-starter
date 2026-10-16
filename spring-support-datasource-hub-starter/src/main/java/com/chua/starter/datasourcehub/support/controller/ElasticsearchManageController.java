package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.ElasticsearchClusterVO;
import com.chua.starter.datasourcehub.support.model.ElasticsearchGraphVO;
import com.chua.starter.datasourcehub.support.model.ElasticsearchIndexDetailVO;
import com.chua.starter.datasourcehub.support.model.ElasticsearchIndexVO;
import com.chua.starter.datasourcehub.support.model.ElasticsearchShardVO;
import com.chua.starter.datasourcehub.support.model.MiddlewareTestVO;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchClusterSettingsDTO;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchIndexDTO;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchMappingDTO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datasourcehub.support.service.ElasticsearchManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Elasticsearch 搜索引擎管理 REST 接口
 * <p>
 * 覆盖三类能力：索引与字段设计（建索引/加字段/删索引，等价于建表与改表）、
 * 集群配置（健康度/节点/设置/分片）、关系图（索引-字段层级 + 别名关系）。
 * </p>
 * <p>
 * 各接口的 {@code datasourceId} 均可缺省，由服务端退化为第一个 elasticsearch 数据源。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控管理", description = "Elasticsearch 搜索引擎管理")
@RestController
@RequestMapping("/ms/monitor/elasticsearch")
@RequiredArgsConstructor
public class ElasticsearchManageController {

    /**
     * Elasticsearch 管理服务
     */
    private final ElasticsearchManageService elasticsearchManageService;

    /**
     * 测试 Elasticsearch 连通性
     *
     * @param request 测试请求，可为空
     * @return 测试结果
     */
    @Operation(summary = "测试 Elasticsearch 连接")
    @PostMapping("/test")
    public ReturnResult<MiddlewareTestVO> test(@RequestBody(required = false) MiddlewareTestDTO request) {
        return call("测试 Elasticsearch 连接", () -> elasticsearchManageService.test(request));
    }

    /**
     * 索引列表
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 索引列表
     */
    @Operation(summary = "Elasticsearch 索引列表")
    @GetMapping("/indices")
    public ReturnResult<List<ElasticsearchIndexVO>> indices(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Elasticsearch 索引", () -> elasticsearchManageService.indices(datasourceId));
    }

    /**
     * 索引详情（字段映射 + 设置）
     *
     * @param index        索引名
     * @param datasourceId 数据源 ID，可为空
     * @return 索引详情
     */
    @Operation(summary = "Elasticsearch 索引详情")
    @GetMapping("/index")
    public ReturnResult<ElasticsearchIndexDetailVO> index(@RequestParam String index,
                                                          @RequestParam(required = false) Long datasourceId) {
        return call("读取 Elasticsearch 索引详情", () -> elasticsearchManageService.index(index, datasourceId));
    }

    /**
     * 新建索引（设计表）
     *
     * @param request 新建请求
     * @return 新建后的索引详情
     */
    @Operation(summary = "新建 Elasticsearch 索引")
    @PostMapping("/index")
    public ReturnResult<ElasticsearchIndexDetailVO> createIndex(@RequestBody ElasticsearchIndexDTO request) {
        return call("新建 Elasticsearch 索引", () -> elasticsearchManageService.createIndex(request));
    }

    /**
     * 删除索引
     *
     * @param index        索引名
     * @param datasourceId 数据源 ID，可为空
     * @return 是否删除成功
     */
    @Operation(summary = "删除 Elasticsearch 索引")
    @DeleteMapping("/index")
    public ReturnResult<Boolean> deleteIndex(@RequestParam String index,
                                             @RequestParam(required = false) Long datasourceId) {
        return call("删除 Elasticsearch 索引", () -> elasticsearchManageService.deleteIndex(index, datasourceId));
    }

    /**
     * 给已有索引追加字段
     *
     * @param request 追加请求
     * @return 更新后的索引详情
     */
    @Operation(summary = "Elasticsearch 追加字段")
    @PostMapping("/mapping")
    public ReturnResult<ElasticsearchIndexDetailVO> addMapping(@RequestBody ElasticsearchMappingDTO request) {
        return call("Elasticsearch 追加字段", () -> elasticsearchManageService.addMapping(request));
    }

    /**
     * 集群信息
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 集群信息
     */
    @Operation(summary = "Elasticsearch 集群信息")
    @GetMapping("/cluster")
    public ReturnResult<ElasticsearchClusterVO> cluster(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Elasticsearch 集群", () -> elasticsearchManageService.cluster(datasourceId));
    }

    /**
     * 分片分配
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 分片列表
     */
    @Operation(summary = "Elasticsearch 分片分配")
    @GetMapping("/shards")
    public ReturnResult<List<ElasticsearchShardVO>> shards(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Elasticsearch 分片", () -> elasticsearchManageService.shards(datasourceId));
    }

    /**
     * 更新集群设置
     *
     * @param request 更新请求
     * @return 更新后的集群信息
     */
    @Operation(summary = "更新 Elasticsearch 集群设置")
    @PutMapping("/cluster/settings")
    public ReturnResult<ElasticsearchClusterVO> updateClusterSettings(
            @RequestBody ElasticsearchClusterSettingsDTO request) {
        return call("更新 Elasticsearch 集群设置",
                () -> elasticsearchManageService.updateClusterSettings(request));
    }

    /**
     * 关系图
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 关系图
     */
    @Operation(summary = "Elasticsearch 关系图")
    @GetMapping("/graph")
    public ReturnResult<ElasticsearchGraphVO> graph(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Elasticsearch 关系图", () -> elasticsearchManageService.graph(datasourceId));
    }

    /**
     * 统一包装：把业务异常转成失败响应
     *
     * @param action   动作名，用于日志与错误提示
     * @param supplier 业务动作
     * @param <T>      返回类型
     * @return 响应
     */
    private <T> ReturnResult<T> call(String action, Supplier<T> supplier) {
        try {
            return ReturnResult.ok(supplier.get());
        } catch (Exception e) {
            log.error("[elasticsearch] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }
}
