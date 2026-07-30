package com.chua.spider.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.spider.support.dto.SpiderDefinitionSaveRequest;
import com.chua.spider.support.service.SpiderCenterService;
import com.chua.spider.support.vo.SpiderDataRecordVo;
import com.chua.spider.support.vo.SpiderDefinitionVo;
import com.chua.spider.support.vo.SpiderExecutionRecordVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 爬虫中心 REST 控制器。
 * <p>对前端 {@code /spider-api/spider/*} 提供接口，实现定义列表/创建/执行记录/采集数据等。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/spider")
@Tag(name = "爬虫中心", description = "爬虫定义、采集数据、执行记录相关接口")
public class SpiderCenterController {

    /**
     * 爬虫中心业务服务
     */
    private final SpiderCenterService spiderService;

    /**
     * 分页查询爬虫定义。
     *
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @param keyword 模糊匹配关键词
     * @return 分页结果
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
     *
     * @param spiderCode 爬虫编码
     * @return 定义视图
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
     *
     * @param req 保存请求
     * @return 持久化后的视图
     */
    @Operation(summary = "保存爬虫定义")
    @PostMapping("/definitions/save")
    public ReturnResult<SpiderDefinitionVo> saveDefinition(@Valid @RequestBody SpiderDefinitionSaveRequest req) {
        return ReturnResult.ok(spiderService.saveDefinition(req));
    }

    /**
     * 删除爬虫定义。
     *
     * @param spiderCode 爬虫编码
     * @return 是否成功
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
     *
     * @param spiderCode 爬虫编码
     * @param spiderStatus 新状态
     * @return 是否成功
     */
    @Operation(summary = "切换启停状态")
    @PostMapping("/definitions/status")
    public ReturnResult<Boolean> toggleStatus(
            @Parameter(description = "爬虫编码") @RequestParam String spiderCode,
            @Parameter(description = "新状态，0/1") @RequestParam Integer spiderStatus
    ) {
        return ReturnResult.ok(spiderService.toggleStatus(spiderCode, spiderStatus));
    }

    /**
     * 分页查询采集数据。
     *
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @param spiderDefinitionCode 爬虫编码
     * @param spiderExecutionNo 执行批次号
     * @param depth 深度过滤
     * @return 分页结果
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
     * 分页查询执行记录。
     *
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @param spiderDefinitionCode 爬虫编码
     * @return 分页结果
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
}
