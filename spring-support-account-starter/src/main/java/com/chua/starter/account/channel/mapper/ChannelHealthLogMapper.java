package com.chua.starter.account.channel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.account.channel.store.ChannelHealthLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 渠道健康探针历史映射器。
 *
 * <p>追加式时序行（只增不改），由 {@code AccountChannelHealthService#save}
 * 随最新态同事务写入，按 {@code (connection_id, created_at)} 查询趋势。</p>
 *
 * @author CH
 * @since 4.0.0.66
 */
@Mapper
public interface ChannelHealthLogMapper extends BaseMapper<ChannelHealthLog> {
}
