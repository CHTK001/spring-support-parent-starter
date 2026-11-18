package com.chua.starter.account.channel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.account.channel.store.SysAccountChannel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 渠道管理-渠道映射器。
 *
 * <p>渠道清单来自 {@code ChannelProvider} SPI，渠道级开关与上游信息落在
 * {@code sys_account_channel}。免费渠道只取本表 {@code enabled=1} 的渠道。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Mapper
public interface SysAccountChannelMapper extends BaseMapper<SysAccountChannel> {
}
