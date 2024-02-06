package com.chua.starter.common.support.provider;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.setting.SettingItem;
import com.chua.starter.common.support.setting.SettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.chua.starter.common.support.constant.Constant.SYSTEM;

/**
 * 设置提供程序
 *
 * @author CH
 */
@RestController
@Slf4j
@RequestMapping("${plugin.captcha.context-path:/v1/sys/setting}")
public class SettingProvider {


    private SettingService settingService;

    public SettingProvider(SettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping
    @Cacheable(cacheManager = SYSTEM, cacheNames = SYSTEM, key = "'sys:setting:list'")
    public ReturnResult<List<SettingItem>> list() {
        return ReturnResult.success(settingService.list());
    }
}
