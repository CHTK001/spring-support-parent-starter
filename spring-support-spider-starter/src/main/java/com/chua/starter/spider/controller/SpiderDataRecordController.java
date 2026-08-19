package com.chua.starter.spider.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.dto.DataRecordVO;
import com.chua.starter.spider.dto.PageResultVO;
import com.chua.starter.spider.dto.SpiderConverter;
import com.chua.starter.spider.entity.SpiderDataRecord;
import com.chua.starter.spider.service.SpiderDataRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/** @author CH */

@Tag(name = "采集数据管理")
@RestController
@RequestMapping("${spider.path:/spider}/records")
public class SpiderDataRecordController {

    /** Record服务 */
    private final SpiderDataRecordService recordService;

    /**
     * 创建 SpiderDataRecordController 实例
     * @param recordService recordService
     */
    public SpiderDataRecordController(SpiderDataRecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询采集记录")
    /**
     * Page
     * @param pageNo pageNo
     * @param pageSize pageSize
     * @param keyword keyword
     * @param spiderCode spiderCode
     * @param pageSize pageSize
     * @param spiderCode spiderCode
     * @param keyword keyword
     * @param keyword keyword
     * @param wrapper wrapper
     * @param spiderId spiderId
     * @param spiderId spiderId
     * @param executionNo executionNo
     * @param executionNo executionNo
     */
    public ReturnResult<PageResultVO<DataRecordVO>> page(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String spiderCode) {
        IPage<SpiderDataRecord> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SpiderDataRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SpiderDataRecord::getRecordTime);
        if (StringUtils.hasText(spiderCode)) {
            wrapper.eq(SpiderDataRecord::getDefinitionCode, spiderCode);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SpiderDataRecord::getTitle, keyword)
                    .or().like(SpiderDataRecord::getSourceUrl, keyword));
        }
        IPage<SpiderDataRecord> result = recordService.page(page, wrapper);
        PageResultVO<DataRecordVO> vo = new PageResultVO<>();
        vo.setPageNo(result.getCurrent());
        vo.setPageSize(result.getSize());
        vo.setTotal(result.getTotal());
        vo.setTotalPages(result.getPages());
        List<DataRecordVO> list = SpiderConverter.toDataRecordVOList(result.getRecords());
        vo.setRecords(list);
        vo.setData(list);
        return ReturnResult.ok(vo);
    }

    @GetMapping("/detail")
    @Operation(summary = "查询记录详情")
    /** Detail */
    public ReturnResult<DataRecordVO> detail(@RequestParam Long spiderId) {
        SpiderDataRecord record = recordService.getById(spiderId);
        return ReturnResult.ok(SpiderConverter.toDataRecordVO(record));
    }

    @DeleteMapping("/{spiderId}")
    @Operation(summary = "删除记录")
    /** 删除 */
    public ReturnResult<Boolean> delete(@PathVariable Long spiderId) {
        return ReturnResult.ok(recordService.removeById(spiderId));
    }

    @GetMapping("/by-execution")
    @Operation(summary = "按执行号查询记录")
    /** ByExecution */
    public ReturnResult<List<DataRecordVO>> byExecution(@RequestParam String executionNo) {
        List<SpiderDataRecord> list = recordService.lambdaQuery()
                .eq(SpiderDataRecord::getExecutionNo, executionNo)
                .orderByDesc(SpiderDataRecord::getRecordTime)
                .list();
        return ReturnResult.ok(SpiderConverter.toDataRecordVOList(list));
    }
}
