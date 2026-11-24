package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiSessionTodo;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话待办（opencode todowrite 镜像） 映射器。
 *
 * @since 4.0.0.52
 * @author CH
 */
@Mapper
public interface SysAiSessionTodoMapper extends BaseMapper<SysAiSessionTodo> {
}
