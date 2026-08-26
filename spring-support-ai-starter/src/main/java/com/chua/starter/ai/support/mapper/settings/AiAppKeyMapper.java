package com.chua.starter.ai.support.mapper.settings;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.settings.SysAiAppKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * AppKey Mapper
 *
 * @since 2026-08-03
 * @author CH
 */
@Mapper
public interface AiAppKeyMapper extends BaseMapper<SysAiAppKey> {
}
