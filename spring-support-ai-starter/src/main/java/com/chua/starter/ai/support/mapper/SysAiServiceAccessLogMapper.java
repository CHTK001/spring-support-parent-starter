package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiServiceAccessLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 服务访问日志数据访问接口。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SysAiServiceAccessLogMapper extends BaseMapper<SysAiServiceAccessLog> {
}
