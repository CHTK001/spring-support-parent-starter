package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiKnowledgeGraphEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 知识图谱实体 映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiKnowledgeGraphEntityMapper extends BaseMapper<SysAiKnowledgeGraphEntity> {
}
