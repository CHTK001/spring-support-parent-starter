package com.chua.starter.ai.support.mapper.settings;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.settings.AiServiceStatus;
import org.apache.ibatis.annotations.Mapper;

/**
 * 服务运行状态 Mapper
 *
 * @since 2026-08-03
 * @author CH
 */
@Mapper
public interface AiServiceStatusMapper extends BaseMapper<AiServiceStatus> {
}
