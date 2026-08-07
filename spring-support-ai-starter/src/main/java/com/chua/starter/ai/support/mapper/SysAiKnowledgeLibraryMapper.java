package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiKnowledgeLibrary;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 知识库 Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SysAiKnowledgeLibraryMapper extends BaseMapper<SysAiKnowledgeLibrary> {
}
