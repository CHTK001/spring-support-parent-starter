package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.dto.KnowledgePointSaveDTO;
import com.chua.starter.eduai.model.vo.KnowledgePointVO;
import com.chua.starter.eduai.service.IEduKnowledgePointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * 知识点管理控制器
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/edu/knowledge-point")
@RequiredArgsConstructor
@Tag(name = "知识点管理")
public class EduKnowledgePointController {

    /** Knowledgepoint服务 */
    private final IEduKnowledgePointService knowledgePointService;

    /**
     * 查询知识点树
     *
     * @param subject    学科
     * @param gradeLevel 学段
     * @param grade      年级
     * @return 知识点树
     * @param false false
     */
    @Operation(summary = "查询知识点树")
    @GetMapping("/tree")
    public ReturnResult<List<KnowledgePointVO>> tree(@RequestParam(required = false) String subject,
                                                     @RequestParam(required = false) String gradeLevel,
                                                     @RequestParam(required = false) String grade) {
        return ReturnResult.ok(knowledgePointService.listTree(subject, gradeLevel, grade));
    }

    /**
     * 新增或更新知识点
     *
     * @param dto 知识点数据
     * @return 知识点ID
     */
    @Operation(summary = "新增或更新知识点")
    @PostMapping("/save")
    public ReturnResult<Long> save(@RequestBody KnowledgePointSaveDTO dto) {
        return ReturnResult.ok(knowledgePointService.saveKnowledgePoint(dto));
    }

    /**
     * 删除知识点（含子节点）
     *
     * @param id 知识点标识
     * @return 是否成功
     */
    @Operation(summary = "删除知识点")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id) {
        return ReturnResult.ok(knowledgePointService.deleteKnowledgePoint(id));
    }
}
