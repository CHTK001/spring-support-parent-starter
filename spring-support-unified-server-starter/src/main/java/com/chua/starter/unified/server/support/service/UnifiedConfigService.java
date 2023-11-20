package com.chua.starter.unified.server.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.unified.server.support.entity.UnifiedConfig;

/**
 * 统一配置服务
 *
 * @author CH
 */
public interface UnifiedConfigService extends IService<UnifiedConfig>{


    /**
     * 通知
     *
     * @param unifiedConfig 统一配置
     * @return {@link Boolean}
     */
    Boolean notifyConfig(UnifiedConfig unifiedConfig);

    /**
     * 保存或更新配置
     *
     * @param t t
     * @return {@link ResultData}<{@link Boolean}>
     */
    ResultData<Boolean> saveOrUpdateConfig(UnifiedConfig t);
}
