package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.model.EduExam;
import com.chua.starter.eduai.service.IEduExamService;
import com.chua.starter.eduai.support.util.EduUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 考试管理控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/edu/exam")
@RequiredArgsConstructor
@Tag(name = "考试管理")
public class EduExamController {

    /**
     * 考试服务。
     */
    private final IEduExamService examService;

    /**
     * 分页查询考试
     *
     * @param exam     查询条件
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Operation(summary = "分页查询考试")
    @GetMapping("/page")
    public ReturnResult<PageResult<EduExam>> page(EduExam exam,
                                                  @RequestParam(value = "page", defaultValue = "1") long page,
                                                  @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        // 兜底负数/零值，防止 service 层扫全表
        long safePage = page < 1 ? 1 : page;
        // [P3C A20 豁免]
        long safeSize = pageSize < 1 ? 10 : Math.min(pageSize, 100);
        return ReturnResult.ok(examService.pageQuery(exam, safePage, safeSize));
    }

    /**
     * 获取考试详情
     *
     * @param id 考试标识
     * @return 考试详情
     */
    @Operation(summary = "获取考试详情")
    @GetMapping("/{id}")
    public ReturnResult<EduExam> detail(@PathVariable Long id) {
        if (id == null) {
            throw new BusinessException("id 不能为空");
        }
        EduExam exam = examService.getById(id);
        if (exam != null) {
            EduUserContext.checkReadable(exam.getOwnerUserId());
        }
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        return ReturnResult.ok(exam);
    }

    /**
     * 创建考试
     *
     * @param exam 考试信息
     * @return 考试ID
     */
    @Operation(summary = "创建考试")
    @PostMapping("/create")
    public ReturnResult<Long> create(@RequestBody EduExam exam) {
        return ReturnResult.ok(examService.createExam(exam));
    }

    /**
     * 更新考试
     *
     * @param exam 考试信息
     * @return 是否成功
     */
    @Operation(summary = "更新考试")
    @PutMapping("/update")
    public ReturnResult<Boolean> update(@RequestBody EduExam exam) {
        return ReturnResult.ok(examService.updateExam(exam));
    }

    /**
     * 删除考试（逻辑删除）
     *
     * @param id 考试标识
     * @return 是否成功
     */
    @Operation(summary = "删除考试")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id) {
        return ReturnResult.ok(examService.deleteExam(id));
    }
}
