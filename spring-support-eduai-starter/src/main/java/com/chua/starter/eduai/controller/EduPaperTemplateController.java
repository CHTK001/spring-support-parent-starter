package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.EduPaperTemplate;
import com.chua.starter.eduai.model.dto.PaperTemplateSaveDTO;
import com.chua.starter.eduai.service.IEduPaperTemplateService;
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
 * 组卷模板管理控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/edu/paper-template")
@RequiredArgsConstructor
@Tag(name = "组卷模板管理")
public class EduPaperTemplateController {

    /**
     * Paper模板服务
     */
    private final IEduPaperTemplateService paperTemplateService;

    /**
     * 分页查询组卷模板
     *
     * @param name     模板名称，模糊匹配
     * @param style    大标题风格
     * @param status   状态
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    @Operation(summary = "分页查询组卷模板")
    @GetMapping("/page")
    public ReturnResult<PageResult<EduPaperTemplate>> page(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return ReturnResult.ok(paperTemplateService.pageQuery(name, style, status, pageNum, pageSize));
    }

    /**
     * 获取组卷模板详情
     *
     * @param id 模板标识
     * @return 模板详情
     */
    @Operation(summary = "获取组卷模板详情")
    @GetMapping("/{id}")
    public ReturnResult<EduPaperTemplate> detail(@PathVariable Long id) {
        return ReturnResult.ok(paperTemplateService.getTemplate(id));
    }

    /**
     * 新增组卷模板
     *
     * @param dto 模板数据
     * @return 新增的模板标识
     */
    @Operation(summary = "新增组卷模板")
    @PostMapping
    public ReturnResult<Long> create(@RequestBody PaperTemplateSaveDTO dto) {
        return ReturnResult.ok(paperTemplateService.createTemplate(dto));
    }

    /**
     * 更新组卷模板
     *
     * @param id  模板标识
     * @param dto 更新内容
     * @return 是否成功
     */
    @Operation(summary = "更新组卷模板")
    @PutMapping("/{id}")
    public ReturnResult<Boolean> update(@PathVariable Long id, @RequestBody PaperTemplateSaveDTO dto) {
        return ReturnResult.ok(paperTemplateService.updateTemplate(id, dto));
    }

    /**
     * 删除组卷模板
     *
     * @param id 模板标识
     * @return 是否成功
     */
    @Operation(summary = "删除组卷模板")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id) {
        return ReturnResult.ok(paperTemplateService.deleteTemplate(id));
    }
}
