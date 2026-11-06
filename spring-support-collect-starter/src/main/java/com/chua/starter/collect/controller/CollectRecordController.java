package com.chua.starter.collect.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.entity.CollectRecord;
import com.chua.starter.collect.service.CollectRecordService;
import com.chua.starter.collect.vo.CollectRecordVO;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采集结果接口。
 *
 * <p>记录内容以有序字段列表返回，前端按字段定义渲染表格。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据采集-结果")
@RestController
@RequestMapping("/v2/collect/record")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectRecordController {

    /**
     * 结果服务。
     */
    private final CollectRecordService recordService;

    /**
     * 结果分页列表。
     *
     * @param query     分页参数
     * @param condition 查询条件
     * @return 结果分页
     */
    @Operation(summary = "结果分页列表")
    @GetMapping("/page")
    public ReturnPageResult<CollectRecordVO> page(@ParameterObject Query<CollectRecord> query,
                                                  @ParameterObject CollectRecord condition) {
        return PageResultUtils.ok(recordService.pageRecords(query.createPage(), condition));
    }

    /**
     * 结果详情。
     *
     * @param recordId 记录ID
     * @return 记录详情
     */
    @Operation(summary = "结果详情")
    @GetMapping("/detail")
    public ReturnResult<CollectRecordVO> detail(@RequestParam Long recordId) {
        return ReturnResult.ok(recordService.detail(recordId));
    }

    /**
     * 删除单条结果。
     *
     * @param recordId 记录ID
     * @return 是否成功
     */
    @Operation(summary = "删除结果")
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> delete(@RequestParam Long recordId) {
        return ReturnResult.ok(recordService.deleteRecord(recordId));
    }

    /**
     * 批量删除结果。
     *
     * @param recordIds 记录ID列表
     * @return 删除条数
     */
    @Operation(summary = "批量删除结果")
    @PostMapping("/deletes")
    public ReturnResult<Integer> deletes(@RequestBody List<Long> recordIds) {
        return ReturnResult.ok(recordService.deleteRecords(recordIds));
    }

    /**
     * 清空结果，流程与任务至少指定一个。
     *
     * @param flowId 流程ID
     * @param taskId 任务ID
     * @return 删除条数
     */
    @Operation(summary = "清空结果")
    @DeleteMapping("/clear")
    public ReturnResult<Integer> clear(@RequestParam(required = false) Integer flowId,
                                       @RequestParam(required = false) Long taskId) {
        return ReturnResult.ok(recordService.clearRecords(flowId, taskId));
    }

    /**
     * 结果总数。
     *
     * @param flowId 流程ID，空表示全库
     * @return 记录数
     */
    @Operation(summary = "结果总数")
    @GetMapping("/count")
    public ReturnResult<Long> count(@RequestParam(required = false) Integer flowId) {
        return ReturnResult.ok(recordService.countRecords(flowId));
    }
}
