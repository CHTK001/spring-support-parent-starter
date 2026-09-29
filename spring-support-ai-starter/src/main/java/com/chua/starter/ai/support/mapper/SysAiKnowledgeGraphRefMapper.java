package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiKnowledgeGraphRef;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识图谱实体/关系与知识点关联 映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiKnowledgeGraphRefMapper extends BaseMapper<SysAiKnowledgeGraphRef> {
}
