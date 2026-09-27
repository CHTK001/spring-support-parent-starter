package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.eduai.model.EduKnowledgeDoc;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库文档 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduKnowledgeDocMapper extends BaseMapper<EduKnowledgeDoc> {

    /**
     * 分页查询知识库文档（XML 语句 pageSelect，按文档实体字段做动态筛选）。
     *
     * @param page  分页参数
     * @param query 筛选条件（仅使用 knowledgeDocName/knowledgeDocSubject/knowledgeDocStatus 字段）
     * @param scope 归属隔离参数
     * @return 文档分页
     */
    IPage<EduKnowledgeDoc> pageSelect(Page<EduKnowledgeDoc> page, @Param("query") EduKnowledgeDoc query,
                                      @Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 关键字检索已索引文档并限制条数（XML 语句 selectSearch）。
     *
     * @param keyword 文档名称关键字，为空时不限定名称
     * @param subject 学科，为空时不限定学科
     * @param limit   取回条数
     * @param scope   归属隔离参数
     * @return 文档列表
     */
    List<EduKnowledgeDoc> selectSearch(@Param("keyword") String keyword,
                                       @Param("subject") String subject,
                                       @Param("limit") int limit,
                                       @Param("scope") EduUserContext.OwnerScope scope);
}
