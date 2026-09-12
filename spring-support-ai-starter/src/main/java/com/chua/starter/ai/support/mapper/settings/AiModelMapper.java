package com.chua.starter.ai.support.mapper.settings;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.settings.SysAiModel;
import org.apache.ibatis.annotations.Param;

/**
   * AI 模型定义 映射器
 *
 * @author CH
 * @since 4.0.0
 */
public interface AiModelMapper extends BaseMapper<SysAiModel> {

    /**
      * 按厂商 标识 物理删除模型（绕过逻辑删除标记，用于同步时的先删后插）。
     *
     * @param providerId 厂商 标识
     * @return 删除的行数
     * @param @Param("providerId" @参数("提供者标识"
     */
    int physicalDeleteByProviderId(@Param("providerId") Long providerId);
}