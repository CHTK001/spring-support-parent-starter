package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import org.apache.ibatis.annotations.Mapper;

/**
 * nomifun 提供商模型 映射器。
 *
 * <p>表 {@code sys_ai_provider_model} 的真实主键是 {@code (provider_id, model)}，
 * 但实体只把 {@code model} 标成 {@code @TableId}（单列主键是 MyBatis-Plus 的要求）。
 * 直接用 {@code updateById} 会把 WHERE 收窄成 {@code model = ?}，在多个供应商有
 * 同名模型时（例如 OPENCODE 与 MINIMAX 都有 {@code minimax-m3}，列排序规则
 * 大小写不敏感）会同时命中别人的行，轻则改错数据，重则把 provider_id 写重
 * 触发主键冲突。写模型行请统一走 {@link #updateByKey(SysAiProviderModel)}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiProviderModelMapper extends BaseMapper<SysAiProviderModel> {

    /**
     * 按真实主键 {@code (provider_id, model)} 更新一行。
     *
     * <p>只把非空字段写进 SET（与 {@code updateById} 的默认策略一致），
     * WHERE 同时约束供应商与模型名，不会波及其他供应商的同名模型。</p>
     *
     * @param row 待更新行，必须带 providerId 与 model
     * @return 影响行数；参数缺主键时返回 0
     */
    default int updateByKey(SysAiProviderModel row) {
        if (row == null || row.getProviderId() == null || row.getModel() == null) {
            return 0;
        }
        return update(row, new LambdaUpdateWrapper<SysAiProviderModel>()
                .eq(SysAiProviderModel::getProviderId, row.getProviderId())
                .eq(SysAiProviderModel::getModel, row.getModel()));
    }
}
