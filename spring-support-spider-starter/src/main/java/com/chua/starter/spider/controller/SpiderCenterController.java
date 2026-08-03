package com.chua.starter.spider.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.dto.SpiderDefinitionSaveRequest;
import com.chua.starter.spider.executor.SpiderExecutionContext;
import com.chua.starter.spider.executor.SpiderExecutionService;
import com.chua.starter.spider.service.SpiderCenterService;
import com.chua.starter.spider.vo.SpiderDataRecordVo;
import com.chua.starter.spider.vo.SpiderDefinitionVo;
import com.chua.starter.spider.vo.SpiderExecutionRecordVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 爬虫中心 REST 控制器。
 * <p>对前端 {@code /spider-api/spider/*} 提供接口，实现定义列表/创建/执行记录/采集数据/执行触发等。
 *
 * <h3>路由规划</h3>
 * <ul>
 *   <li>{@code /spider/definitions/**} — 爬虫定义 CRUD</li>
 *   <li>{@code /spider/data-records/**} — 采集数据分页</li>
 *   <li>{@code /spider/execution-records/**} — 执行记录分页</li>
 *   <li>{@code /spider/executions/**} — 启动/停止/查询进度</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/spider")
@Tag(name = "爬虫中心", description = "爬虫定义、采集数据、执行记录、运行控制相关接口")
public class SpiderCenterController {

    /**
     * 爬虫中心业务服务
     */
    private final SpiderCenterService spiderService;

    /**
     * 爬虫执行服务
     */
    private final SpiderExecutionService executionService;

    // ==================== 爬虫定义 ====================

    /**
     * 分页查询爬虫定义。
     */
    @Operation(summary = "分页查询爬虫定义")
    @GetMapping("/definitions/page")
    public ReturnResult<Page<SpiderDefinitionVo>> pageDefinitions(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNo,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword
    ) {
        return ReturnResult.ok(spiderService.pageDefinitions(pageNo, pageSize, keyword));
    }

    /**
     * 获取爬虫定义详情。
     */
    @Operation(summary = "爬虫定义详情")
    @GetMapping("/definitions/detail")
    public ReturnResult<SpiderDefinitionVo> getDefinition(
            @Parameter(description = "爬虫编码") @RequestParam String spiderCode
    ) {
        return ReturnResult.ok(spiderService.getDefinition(spiderCode));
    }

    /**
     * 保存或更新爬虫定义。
     */
    @Operation(summary = "保存爬虫定义")
    @PostMapping("/definitions/save")
    public ReturnResult<SpiderDefinitionVo> saveDefinition(@Valid @RequestBody SpiderDefinitionSaveRequest req) {
        return ReturnResult.ok(spiderService.saveDefinition(req));
    }

    /**
     * 删除爬虫定义。
     */
    @Operation(summary = "删除爬虫定义")
    @DeleteMapping("/definitions/delete")
    public ReturnResult<Boolean> deleteDefinition(
            @Parameter(description = "爬虫编码") @RequestParam String spiderCode
    ) {
        return ReturnResult.ok(spiderService.deleteDefinition(spiderCode));
    }

    /**
     * 切换爬虫启停状态。
     */
    @Operation(summary = "切换启停状态")
    @PostMapping("/definitions/status")
    public ReturnResult<Boolean> toggleStatus(
            @Parameter(description = "爬虫编码") @RequestParam String spiderCode,
            @Parameter(description = "新状态，0/1") @RequestParam Integer spiderStatus
    ) {
        return ReturnResult.ok(spiderService.toggleStatus(spiderCode, spiderStatus));
    }

    // ==================== 采集数据 ====================

    /**
     * 分页查询采集数据。
     */
    @Operation(summary = "分页查询采集数据")
    @GetMapping("/data-records/page")
    public ReturnResult<Page<SpiderDataRecordVo>> pageDataRecords(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNo,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "50") int pageSize,
            @Parameter(description = "爬虫编码") @RequestParam String spiderDefinitionCode,
            @Parameter(description = "执行批次号") @RequestParam(required = false) String spiderExecutionNo,
            @Parameter(description = "深度过滤") @RequestParam(required = false) Integer depth
    ) {
        return ReturnResult.ok(spiderService.pageDataRecords(
                pageNo, pageSize, spiderDefinitionCode, spiderExecutionNo, depth));
    }

    /**
     * 查看采集数据详情。
     */
    @Operation(summary = "采集数据详情")
    @GetMapping("/data-records/{id}")
    public ReturnResult<SpiderDataRecordVo> getDataRecord(@PathVariable Long id) {
        return ReturnResult.ok(spiderService.getDataRecord(id));
    }

    // ==================== 执行记录 ====================

    /**
     * 分页查询执行记录。
     */
    @Operation(summary = "分页查询执行记录")
    @GetMapping("/execution-records/page")
    public ReturnResult<Page<SpiderExecutionRecordVo>> pageExecutions(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNo,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "爬虫编码") @RequestParam String spiderDefinitionCode
    ) {
        return ReturnResult.ok(spiderService.pageExecutions(pageNo, pageSize, spiderDefinitionCode));
    }

    // ==================== 执行控制 ====================

    /**
     * 启动爬虫执行。
     *
     * @param spiderCode 爬虫业务编码
     * @return 批次号，前端按此轮询进度
     */
    @Operation(summary = "启动爬虫执行")
    @PostMapping("/executions/start")
    public ReturnResult<String> start(@Parameter(description = "爬虫编码") @RequestParam String spiderCode) {
        try {
            return ReturnResult.ok(executionService.start(spiderCode, "MANUAL"));
        } catch (Exception e) {
            log.error("启动爬虫失败: {}", e.getMessage(), e);
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 停止指定批次的执行。
     */
    @Operation(summary = "停止爬虫执行")
    @PostMapping("/executions/stop")
    public ReturnResult<Boolean> stop(@Parameter(description = "批次号") @RequestParam String executionNo) {
        return ReturnResult.ok(executionService.stop(executionNo));
    }

    /**
     * 查询执行进度。
     */
    @Operation(summary = "查询执行进度")
    @GetMapping("/executions/status")
    public ReturnResult<Map<String, Object>> status(
            @Parameter(description = "批次号") @RequestParam String executionNo) {
        SpiderExecutionContext context = executionService.status(executionNo);
        if (context == null) {
            return ReturnResult.ok(Map.of("status", "NOT_FOUND"));
        }
        return ReturnResult.ok(Map.of(
                "executionNo", context.getExecutionNo(),
                "spiderCode", String.valueOf(context.getSpiderCode()),
                "status", String.valueOf(context.getStatus()),
                "completedCount", context.getCompletedCount(),
                "totalCount", context.getTotalCount(),
                "collectedCount", context.getCollectedCount(),
                "maxDepthReached", context.getMaxDepthReached()
        ));
    }

    /**
     * 列出所有正在运行的批次。
     */
    @Operation(summary = "列出正在运行的爬虫")
    @GetMapping("/executions/running")
    public ReturnResult<List<SpiderExecutionContext>> listRunning() {
        return ReturnResult.ok(executionService.listRunning());
    }
}
