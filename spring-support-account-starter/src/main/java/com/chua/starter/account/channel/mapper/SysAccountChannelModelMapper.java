package com.chua.starter.account.channel.mapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 渠道管理-渠道模型映射器。
 *
 * <p>表 {@code sys_account_channel_model} 的真实主键是 {@code (provider_id, model)}，
 * 但实体只把 {@code model} 标成 {@code @TableId}（单列主键是 MyBatis-Plus 的要求）。
 * 直接用 {@code updateById} 会把 WHERE 收窄成 {@code model = ?}，在多个渠道有同名模型时
 * （例如 OPENCODE 与 MINIMAX 都有 {@code minimax-m3}，列排序规则大小写不敏感）
 * 会同时命中别人的行，轻则改错数据，重则把 provider_id 写重触发主键冲突。
 * 写模型行请统一走 {@link #updateByKey(SysAccountChannelModel)}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAccountChannelModelMapper extends BaseMapper<SysAccountChannelModel> {

    /**
     * 按真实主键 {@code (provider_id, model)} 更新一行。
     *
     * <p>只把非空字段写进 SET（与 {@code updateById} 的默认策略一致），
     * WHERE 同时约束渠道与模型名，不会波及其他渠道的同名模型。</p>
     *
     * @param row 待更新行，必须带 providerId 与 model
     * @return 影响行数；参数缺主键时返回 0
     */
    default int updateByKey(SysAccountChannelModel row) {
        if (row == null || row.getProviderId() == null || row.getModel() == null) {
            return 0;
        }
        return update(row, new LambdaUpdateWrapper<SysAccountChannelModel>()
                .eq(SysAccountChannelModel::getProviderId, row.getProviderId())
                .eq(SysAccountChannelModel::getModel, row.getModel()));
    }
}
