package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiModelMetrics;
import org.apache.ibatis.annotations.Mapper;

/**
 * 全量模型指标库 映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiModelMetricsMapper extends BaseMapper<SysAiModelMetrics> {
}
