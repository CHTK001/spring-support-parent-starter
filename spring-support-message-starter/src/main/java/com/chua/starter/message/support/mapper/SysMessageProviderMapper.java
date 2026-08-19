package com.chua.starter.message.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.message.support.entity.SysMessageProvider;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息提供商 Mapper
 *
 * @since 2026/08/03
 * @author CH
 */
@Mapper
public interface SysMessageProviderMapper extends BaseMapper<SysMessageProvider> {
}
