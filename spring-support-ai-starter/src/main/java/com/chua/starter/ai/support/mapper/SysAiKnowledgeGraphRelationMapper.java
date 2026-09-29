package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiKnowledgeGraphRelation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 知识图谱关系 映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiKnowledgeGraphRelationMapper extends BaseMapper<SysAiKnowledgeGraphRelation> {
}
