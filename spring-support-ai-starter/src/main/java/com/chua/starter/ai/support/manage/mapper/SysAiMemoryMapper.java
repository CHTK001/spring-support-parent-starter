package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiMemory;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 记忆体表 映射器（MyBatis-Plus 标准 CRUD）。
 *
 * <p>召回、去重与维护均通过 {@code LambdaQueryWrapper} 组装条件，不提供自定义 XML 语句：
 * 全前缀列名配 {@code SELECT *} 的自定义 resultType 会映射不到主键与归属列，
 * 使更新静默命中 0 行。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiMemoryMapper extends BaseMapper<SysAiMemory> {
}
