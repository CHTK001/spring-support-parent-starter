package com.chua.starter.config.server.support.controller;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.manager.DataManager;
import com.chua.starter.config.server.support.properties.ConfigServerProperties;
import com.chua.starter.config.server.support.repository.ConfigurationCenterInfo;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Set;

/**
 * 配置中心
 * @author CH
 * @since 2022/8/1 14:54
 */
@RequestMapping("/v1/configuration")
@RestController
public class ConfigurationCenterController implements ApplicationContextAware {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private ConfigServerProperties configServerProperties;

    private DataManager dataManager;

    /**
     * 配置頁面
     * @return 頁面
     */
    @GetMapping("/page")
    @ResponseBody
    @SuppressWarnings("ALL")
    public ReturnPageResult<ConfigurationCenterInfo> configList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "profile", required = false) String profile
    ) {

        Page infoPage = dataManager.findAll(ConfigConstant.CONFIG, page - 1, pageSize, profile);
        return ReturnPageResult.ok(ReturnPageResult.<ConfigurationCenterInfo>newBuilder()
                .data(infoPage.getContent()).page(page).pageSize(pageSize).totalPages(infoPage.getTotalPages()).total(infoPage.getTotalElements()).build());
    }

    /**
     * 配置頁面
     *
     * @return 頁面
     */
    @PostMapping("/save")
    @ResponseBody
    public ReturnResult<ConfigurationCenterInfo> configSave(@RequestBody ConfigurationCenterInfo configValue) {
        dataManager.save(ConfigConstant.CONFIG, configValue);
        dataManager.notifyConfig(ConfigConstant.CONFIG, configValue.getConfigId(), configValue.getConfigValue(), configValue.getDisable(), null);
        return ReturnResult.ok(configValue);
    }

    /**
     * 刪除頁面
     *
     * @return 頁面
     */
    @DeleteMapping("/delete")
    public ReturnResult<Void> configSave(String configId) {
        dataManager.delete(ConfigConstant.CONFIG, configId);
        return ReturnResult.ok();
    }

    /**
     * 环境
     *
     * @return 环境
     */
    @PostMapping("/profile")
    @ResponseBody
    public ReturnResult<Set<String>> profile() {
        return ReturnResult.ok(dataManager.profile(ConfigConstant.CONFIG));
    }

    /**
     * 环境
     *
     * @return 环境
     */
    @PostMapping("/applications")
    @ResponseBody
    public ReturnResult<Set<String>> applications() {
        return ReturnResult.ok(dataManager.applications(ConfigConstant.CONFIG));
    }

    /**
     * 配置頁面
     *
     * @return 頁面
     */
    @PostMapping("/update")
    @ResponseBody
    public ReturnResult<Void> configUpdate(
            @RequestParam(value = "config-id", defaultValue = "") Integer configId,
            @RequestParam(value = "config-value", defaultValue = "") String configValue,
            @RequestParam(value = "config-disable", defaultValue = "") Integer disable
    ) {

        dataManager.notifyConfig(ConfigConstant.CONFIG, configId, configValue, disable, null);
        return ReturnResult.ok();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.dataManager = ServiceProvider.of(DataManager.class).getExtension(configServerProperties.getDataManager());
    }
}
