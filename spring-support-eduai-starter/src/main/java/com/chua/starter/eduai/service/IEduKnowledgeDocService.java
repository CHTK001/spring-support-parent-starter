package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduKnowledgeDoc;
import com.chua.starter.eduai.model.dto.KnowledgeDocUploadDTO;
import com.chua.starter.eduai.model.vo.KnowledgeDocVO;

/**
 * 知识库文档服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduKnowledgeDocService extends IService<EduKnowledgeDoc> {

    /**
     * 分页查询知识库文档
     *
     * @param query 查询条件
     * @return 分页结果
     * @param page page
     * @param pageSize page大小
     */
    PageResult<KnowledgeDocVO> pageQuery(EduKnowledgeDoc query, long page, long pageSize);

    /**
     * 上传文档
     *
     * @param dto 文档上传数据
     * @return 文档ID
     */
    Long uploadDoc(KnowledgeDocUploadDTO dto);

    /**
     * 语义检索
     *
     * @param keyword 检索关键字
     * @param subject 学科
     * @param limit   返回数量
     * @return 检索结果
     */
    PageResult<KnowledgeDocVO> search(String keyword, String subject, int limit);

    /**
     * 删除文档
     *
     * @param id 文档标识
     * @return 是否成功
     */
    boolean deleteDoc(Long id);
}
