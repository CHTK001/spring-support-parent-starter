package com.chua.starter.account.channel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.account.channel.store.ChannelHealth;
import org.apache.ibatis.annotations.Mapper;

/**
 * 渠道健康探针记录映射器。
 *
 * <p>以「渠道 + 连接」为唯一键（{@code uk_provider_connection}），由探针任务写入、
 * 路由层与安全层读取。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Mapper
public interface ChannelHealthMapper extends BaseMapper<ChannelHealth> {
}
