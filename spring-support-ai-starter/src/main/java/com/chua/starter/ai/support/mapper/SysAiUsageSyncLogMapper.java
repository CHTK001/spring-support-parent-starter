package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiUsageSyncLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 用量同步日志 映射器。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SysAiUsageSyncLogMapper extends BaseMapper<SysAiUsageSyncLog> {
}
