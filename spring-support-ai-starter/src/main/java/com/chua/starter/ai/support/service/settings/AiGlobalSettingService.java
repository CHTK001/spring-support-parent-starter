package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiGlobalSettingDTO;

/**
* 全局设置服务接口（单条记录 标识=1）
*
* @since 2026-08-03
* @author CH
 */
public interface AiGlobalSettingService {

    /**
    * 获取全局设置。
    *
    * @return 全局设置 DTO
    */
    AiGlobalSettingDTO get();

    /**
    * 更新全局设置。
    *
    * @param setting 全局设置 DTO
    * @return 更新后的全局设置 DTO
    */
    AiGlobalSettingDTO update(AiGlobalSettingDTO setting);
}
