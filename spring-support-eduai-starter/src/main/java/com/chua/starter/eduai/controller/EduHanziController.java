package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.model.EduHanzi;
import com.chua.starter.eduai.service.IEduHanziService;
import com.chua.starter.eduai.support.util.EduUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
 * 汉字基础数据控制器（字 / 读音 / 笔顺 / 字画 / 释义 …）。
 *
 * @since 4.0.0.43
 * @author CH
 */
@RestController
@RequestMapping("/v2/edu/hanzi")
@RequiredArgsConstructor
@Tag(name = "汉字基础数据")
public class EduHanziController {

    private final IEduHanziService hanziService;

    /**
     * 分页查询汉字。
     *
     * @param keyword    关键字（字 / 拼音 / 释义）
     * @param gradeLevel 学段（可空）
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @return 分页结果
     */
    @Operation(summary = "分页查询汉字")
    @GetMapping("/page")
    public ReturnResult<PageResult<EduHanzi>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        int pNum = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int pSize = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 100);
        return ReturnResult.ok(hanziService.pageQuery(keyword, gradeLevel, pNum, pSize));
    }

    /**
     * 汉字详情。
     *
     * @param id 汉字标识
     * @return 汉字；不存在返回 null
     */
    @Operation(summary = "汉字详情")
    @GetMapping("/{id}")
    public ReturnResult<EduHanzi> detail(@PathVariable Long id) {
        if (id == null) {
            throw new BusinessException("id 不能为空");
        }
        EduHanzi hanzi = hanziService.getById(id);
        if (hanzi != null) {
            EduUserContext.checkReadable(hanzi.getOwnerUserId());
        }
        if (hanzi == null) {
            throw new BusinessException("汉字不存在");
        }
        return ReturnResult.ok(hanzi);
    }

    /**
     * 保存汉字（同一个字重复提交则更新）。
     *
     * @param entity 汉字
     * @return 汉字标识
     */
    @Operation(summary = "保存汉字")
    @PostMapping("/save")
    public ReturnResult<Long> save(@RequestBody EduHanzi entity) {
        return ReturnResult.ok(hanziService.saveHanzi(entity));
    }

    /**
     * 删除汉字。
     *
     * @param id 汉字标识
     * @return 是否成功
     */
    @Operation(summary = "删除汉字")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id) {
        return ReturnResult.ok(hanziService.deleteHanzi(id));
    }

    /**
     * 识字练习题：服务端自动出题（读音 / 笔画），题目里带笔顺与释义供答完讲解。
     *
     * @param gradeLevel 学段（可空）
     * @param count      题目数量（服务端夹在 1~20）
     * @return 题目列表
     */
    @Operation(summary = "汉字识字练习-出题")
    @GetMapping("/practice")
    public ReturnResult<List<Map<String, Object>>> practice(
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(defaultValue = "8") int count) {
        // 限制 1~20，防止过大值
        int safeCount = count <= 0 ? 8 : Math.min(count, 20);
        return ReturnResult.ok(hanziService.generatePractice(gradeLevel, safeCount));
    }
}
