package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiGenerateTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生成任务 映射器。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SysAiGenerateTaskMapper extends BaseMapper<SysAiGenerateTask> {
}
