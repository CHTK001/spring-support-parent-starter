package com.chua.starter.unified.server.support.controller;

import com.chua.starter.mybatis.controller.BaseController;
import com.chua.starter.unified.server.support.entity.UnifiedConfig;
import com.chua.starter.unified.server.support.service.UnifiedConfigService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置中心接口
 * @author CH
 */
@RestController
@RequestMapping("v1/config")
@AllArgsConstructor
public class UnifiedConfigController extends BaseController<UnifiedConfigService, UnifiedConfig> {

    private final UnifiedConfigService unifiedConfigService;


    @Override
    public UnifiedConfigService getService() {
        return unifiedConfigService;
    }
}
