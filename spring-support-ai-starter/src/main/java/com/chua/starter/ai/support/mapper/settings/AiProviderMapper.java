package com.chua.starter.ai.support.mapper.settings;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.settings.AiProvider;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型供应商 Mapper
 *
 * @author CH
 * @since 2026-08-03
 */
@Mapper
public interface AiProviderMapper extends BaseMapper<AiProvider> {
}
