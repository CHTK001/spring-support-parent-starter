package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.EduKnowledgeDoc;
import com.chua.starter.eduai.model.dto.KnowledgeDocUploadDTO;
import com.chua.starter.eduai.model.vo.KnowledgeDocVO;
import com.chua.starter.eduai.service.IEduKnowledgeDocService;
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

/**
* 知识库文档管理控制器
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/edu/knowledge-doc")
@RequiredArgsConstructor
@Tag(name = "知识库文档管理")
public class EduKnowledgeDocController {

    /** 知识doc服务 */
    private final IEduKnowledgeDocService knowledgeDocService;

    /**
    * 分页查询知识库文档
    *
    * @param query    查询条件
    * @param page     页码
    * @param pageSize 每页条数
    * @return 分页结果
     */
    @Operation(summary = "分页查询知识库文档")
    @GetMapping("/page")
    public ReturnResult<PageResult<KnowledgeDocVO>> page(EduKnowledgeDoc query,
                                                         @RequestParam(value = "page", defaultValue = "1") long page,
                                                         @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return ReturnResult.ok(knowledgeDocService.pageQuery(query, page, pageSize));
    }

    /**
    * 上传文档
    *
    * @param dto 文档上传数据
    * @return 文档ID
     */
    @Operation(summary = "上传文档")
    @PostMapping("/upload")
    public ReturnResult<Long> upload(@RequestBody KnowledgeDocUploadDTO dto) {
        return ReturnResult.ok(knowledgeDocService.uploadDoc(dto));
    }

    /**
    * 语义检索
    *
    * @param keyword 检索关键字
    * @param subject 学科
    * @param limit   返回数量
    * @return 检索结果
     */
    @Operation(summary = "语义检索")
    @GetMapping("/search")
    public ReturnResult<PageResult<KnowledgeDocVO>> search(@RequestParam String keyword,
                                                           @RequestParam(required = false) String subject,
                                                           @RequestParam(defaultValue = "10") int limit) {
        return ReturnResult.ok(knowledgeDocService.search(keyword, subject, limit));
    }

    /**
    * 删除文档
    *
    * @param id 文档标识
    * @return 是否成功
     */
    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id) {
        return ReturnResult.ok(knowledgeDocService.deleteDoc(id));
    }
}
