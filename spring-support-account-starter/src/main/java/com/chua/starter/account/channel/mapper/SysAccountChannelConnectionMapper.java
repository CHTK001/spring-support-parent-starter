package com.chua.starter.account.channel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.account.channel.store.SysAccountChannelConnection;
import org.apache.ibatis.annotations.Mapper;

/**
 * 渠道管理-渠道连接映射器。
 *
 * <p>连接按渠道键（{@code provider_id}）分组，承载绑定凭据与账号类型；
 * 凭据加密后落库，读接口只回 {@code hasCredentials}。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Mapper
public interface SysAccountChannelConnectionMapper extends BaseMapper<SysAccountChannelConnection> {
}
