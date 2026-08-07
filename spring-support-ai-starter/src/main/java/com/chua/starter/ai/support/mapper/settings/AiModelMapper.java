package com.chua.starter.ai.support.mapper.settings;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.settings.AiModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * AI 模型定义 Mapper
 *
 * @author CH
 */
public interface AiModelMapper extends BaseMapper<AiModel> {

    /**
     * 按厂商 ID 物理删除模型（绕过逻辑删除标记，用于同步时的先删后插）。
     *
     * @param providerId 厂商 ID
     * @return 删除的行数
     */
    @Delete("DELETE FROM sys_ai_model WHERE sys_ai_model_provider_id = #{providerId}")
    int physicalDeleteByProviderId(@Param("providerId") Long providerId);
}