package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.InfluxdbBucketVO;
import com.chua.starter.datasourcehub.support.model.InfluxdbMeasurementVO;
import com.chua.starter.datasourcehub.support.model.InfluxdbOrgVO;
import com.chua.starter.datasourcehub.support.model.InfluxdbQueryResultVO;
import com.chua.starter.datasourcehub.support.model.MiddlewareTestVO;
import com.chua.starter.datasourcehub.support.model.dto.InfluxdbBucketDTO;
import com.chua.starter.datasourcehub.support.model.dto.InfluxdbQueryDTO;
import com.chua.starter.datasourcehub.support.model.dto.InfluxdbWriteDTO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datasourcehub.support.service.InfluxdbManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * InfluxDB 时序数据库管理 REST 接口。
 *
 * <p>四个接口的 {@code datasourceId} 均可缺省，由服务端退化为第一个 influxdb 数据源，
 * 以便页面在只登记一个数据源时不必先选。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控管理", description = "InfluxDB 时序数据库管理")
@RestController
@RequestMapping("/ms/monitor/influxdb")
@RequiredArgsConstructor
public class InfluxdbManageController {

    /**
     * InfluxDB 管理服务
     */
    private final InfluxdbManageService influxdbManageService;

    /**
     * 测试 InfluxDB 连通性。
     *
     * @param request 测试请求，可为空
     * @return 测试结果
     */
    @Operation(summary = "测试 InfluxDB 连接")
    @PostMapping("/test")
    public ReturnResult<MiddlewareTestVO> test(@RequestBody(required = false) MiddlewareTestDTO request) {
        return call("测试 InfluxDB 连接", () -> influxdbManageService.test(request));
    }

    /**
     * 查询 Bucket 列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @return Bucket 列表
     */
    @Operation(summary = "InfluxDB Bucket 列表")
    @GetMapping("/buckets")
    public ReturnResult<List<InfluxdbBucketVO>> buckets(@RequestParam(required = false) Long datasourceId) {
        return call("读取 InfluxDB Bucket", () -> influxdbManageService.buckets(datasourceId));
    }

    /**
     * 查询度量列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @param bucket       Bucket 名，可空时取第一个 Bucket
     * @return 度量列表
     */
    @Operation(summary = "InfluxDB 度量列表")
    @GetMapping("/measurements")
    public ReturnResult<List<InfluxdbMeasurementVO>> measurements(@RequestParam(required = false) Long datasourceId,
                                                                 @RequestParam(required = false) String bucket) {
        return call("读取 InfluxDB 度量", () -> influxdbManageService.measurements(datasourceId, bucket));
    }

    /**
     * 执行 Flux 查询。
     *
     * @param request 查询请求
     * @return 查询结果
     */
    @Operation(summary = "执行 InfluxDB Flux 查询")
    @PostMapping("/query")
    public ReturnResult<InfluxdbQueryResultVO> query(@RequestBody InfluxdbQueryDTO request) {
        return call("执行 InfluxDB 查询", () -> influxdbManageService.query(request));
    }

    /**
     * 读取当前数据源对应的组织信息。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 组织 ID 与名称
     */
    @Operation(summary = "InfluxDB Org 信息")
    @GetMapping("/org")
    public ReturnResult<InfluxdbOrgVO> org(@RequestParam(required = false) Long datasourceId) {
        return call("读取 InfluxDB Org", () -> influxdbManageService.org(datasourceId));
    }

    /**
     * 新建 Bucket。
     *
     * @param request 新建请求
     * @return 新建后的 Bucket
     */
    @Operation(summary = "新建 InfluxDB Bucket")
    @PostMapping("/bucket")
    public ReturnResult<InfluxdbBucketVO> createBucket(@RequestBody InfluxdbBucketDTO request) {
        return call("新建 InfluxDB Bucket", () -> influxdbManageService.createBucket(request));
    }

    /**
     * 删除 Bucket。
     *
     * @param datasourceId 数据源 ID，可空
     * @param bucketId      Bucket ID（UUID）
     * @return 是否删除成功
     */
    @Operation(summary = "删除 InfluxDB Bucket")
    @DeleteMapping("/bucket/{bucketId}")
    public ReturnResult<Boolean> deleteBucket(@RequestParam(required = false) Long datasourceId,
                                              @PathVariable String bucketId) {
        return call("删除 InfluxDB Bucket", () -> {
            influxdbManageService.deleteBucket(datasourceId, bucketId);
            return Boolean.TRUE;
        });
    }

    /**
     * 编辑 Bucket 保留期。
     *
     * @param datasourceId   数据源 ID，可空
     * @param bucketId       Bucket ID（UUID）
     * @param request        保留期请求
     * @return 是否更新成功
     */
    @Operation(summary = "编辑 InfluxDB Bucket 保留期")
    @PutMapping("/bucket/{bucketId}")
    public ReturnResult<Boolean> updateBucketRetention(@RequestParam(required = false) Long datasourceId,
                                                      @PathVariable String bucketId,
                                                      @RequestBody InfluxdbBucketDTO request) {
        return call("编辑 InfluxDB Bucket 保留期", () -> {
            influxdbManageService.updateBucketRetention(datasourceId, bucketId,
                    request == null ? null : request.retentionHours());
            return Boolean.TRUE;
        });
    }

    /**
     * 写入数据点（line protocol）。
     *
     * @param request 写入请求
     * @return 是否写入成功
     */
    @Operation(summary = "写入 InfluxDB 数据点")
    @PostMapping("/write")
    public ReturnResult<Boolean> write(@RequestBody InfluxdbWriteDTO request) {
        return call("写入 InfluxDB 数据点", () -> {
            influxdbManageService.write(request);
            return Boolean.TRUE;
        });
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
            log.error("[influxdb] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }
}
