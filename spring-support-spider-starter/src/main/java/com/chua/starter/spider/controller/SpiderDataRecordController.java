package com.chua.starter.spider.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.entity.SpiderDataRecord;
import com.chua.starter.spider.service.SpiderDataRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采集数据记录查询接口。
 *
 * <p>提供采集结果的查询，支持按爬虫编码过滤，路径前缀
 * {@code /spider-api/spider/data-records}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "采集数据记录")
@RestController
@RequestMapping("${spider.path:/spider-api/spider}/data-records")
public class SpiderDataRecordController {

    /**
     * 采集数据记录服务
     */
    private final SpiderDataRecordService recordService;

    /**
     * 构造方法。
     *
     * @param recordService 采集数据记录服务
     */
    public SpiderDataRecordController(SpiderDataRecordService recordService) {
        this.recordService = recordService;
    }

    /**
     * 按爬虫编码查询采集记录。
     *
     * @param definitionCode 爬虫编码，为空查询全部
     * @return 采集记录列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询采集数据记录")
    public ReturnResult<List<SpiderDataRecord>> list(
            @RequestParam(required = false) String definitionCode) {
        if (definitionCode == null || definitionCode.isBlank()) {
            return ReturnResult.ok(recordService.list());
        }
        return ReturnResult.ok(recordService.list(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SpiderDataRecord>lambdaQuery()
                        .eq(SpiderDataRecord::getDefinitionCode, definitionCode)));
    }

    /**
     * 按 ID 查询采集记录。
     *
     * @param id 主键 ID
     * @return 采集记录
     */
    @GetMapping("/{id}")
    @Operation(summary = "按 ID 查询采集记录")
    public ReturnResult<SpiderDataRecord> get(@PathVariable Long id) {
        return ReturnResult.ok(recordService.getById(id));
    }
}