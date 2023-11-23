package com.chua.starter.config.server.support.controller;

import com.chua.common.support.collection.ImmutableBuilder;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.entity.ConfigurationBeanInfo;
import com.chua.starter.config.server.support.entity.ConfigurationCenterInfo;
import com.chua.starter.config.server.support.entity.ConfigurationSubscribeInfo;
import com.chua.starter.config.server.support.manager.DataManager;
import com.chua.starter.config.server.support.properties.ConfigServerProperties;
import com.chua.starter.config.server.support.query.DetailUpdate;
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
@RequestMapping("/v1/bean")
@RestController
public class ConfigurationBeanController implements ApplicationContextAware {

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
    public ReturnPageResult<ConfigurationBeanInfo> configList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "profile", required = false) String profile
    ) {

        Page infoPage = dataManager.findAll(ConfigConstant.BEAN, page - 1, pageSize, profile);
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
    public ReturnResult<ConfigurationBeanInfo> configSave(@RequestBody ConfigurationBeanInfo configValue) {
        dataManager.save(ConfigConstant.BEAN, configValue);
        ConfigurationSubscribeInfo configurationSubscribeInfo = new ConfigurationSubscribeInfo();
        configurationSubscribeInfo.setSubscribeProfile(configValue.getBeanProfile());
        configurationSubscribeInfo.setSubscribeType(ConfigConstant.BEAN);
        dataManager.notifyConfig(ConfigConstant.BEAN,
                configurationSubscribeInfo,  configurationSubscribeInfo);
        return ReturnResult.ok(configValue);
    }

    /**
     * 刪除頁面
     *
     * @return 頁面
     */
    @DeleteMapping("/delete")
    public ReturnResult<Void> configSave(String configId) {
        dataManager.delete(ConfigConstant.BEAN, configId);
        return ReturnResult.ok();
    }
    /**
     * 获取脚本
     *
     * @return 获取脚本
     */
    @GetMapping("/detail")
    public ReturnResult<Object> detail(String configId) {
        return ReturnResult.ok(dataManager.getDetail(ConfigConstant.BEAN, configId));
    }
    /**
     * 修改脚本
     *
     * @return 修改脚本
     */
    @PostMapping("/detailUpdate")
    public ReturnResult<Object> detailUpdate(@RequestBody DetailUpdate detailUpdate) {
        Object update = dataManager.detailUpdate(ConfigConstant.BEAN, detailUpdate);
        if(null != update) {
            ConfigurationSubscribeInfo configurationSubscribeInfo = new ConfigurationSubscribeInfo();
            configurationSubscribeInfo.setSubscribeProfile(update.toString());
            configurationSubscribeInfo.setSubscribeType(ConfigConstant.BEAN);
            dataManager.notifyConfig(ConfigConstant.BEAN,
                    configurationSubscribeInfo, ImmutableBuilder
                            .builderOfStringMap().put("value", detailUpdate.getContent()).build());
            return ReturnResult.ok(update);
        }

        return ReturnResult.illegal();
    }
    /**
     * 环境
     *
     * @return 环境
     */
    @PostMapping("/profile")
    @ResponseBody
    public ReturnResult<Set<String>> profile() {
        return ReturnResult.ok(dataManager.profile(ConfigConstant.BEAN));
    }

    /**
     * 环境
     *
     * @return 环境
     */
    @PostMapping("/applications")
    @ResponseBody
    public ReturnResult<Set<String>> applications() {
        return ReturnResult.ok(dataManager.applications(ConfigConstant.BEAN));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.dataManager = ServiceProvider.of(DataManager.class).getExtension(configServerProperties.getDataManager());
    }
}
