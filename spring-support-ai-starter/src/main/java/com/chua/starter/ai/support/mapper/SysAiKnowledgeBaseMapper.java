package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 知识库元数据 映射器。
 *
 * <p>只继承 MyBatis-Plus 的 {@link BaseMapper}，不写注解 SQL：
 * 本仓强制「SQL 只能写在 {@code *Mapper.xml}」，注解式
 * {@code @Select/@Insert/@Update/@Delete} 一律禁止。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiKnowledgeBaseMapper extends BaseMapper<SysAiKnowledgeBase> {
}
