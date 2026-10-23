package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderMetric;
import org.apache.ibatis.annotations.Mapper;

/**
 * 厂商指标系数表 映射器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiProviderMetricMapper extends BaseMapper<SysAiProviderMetric> {
}
