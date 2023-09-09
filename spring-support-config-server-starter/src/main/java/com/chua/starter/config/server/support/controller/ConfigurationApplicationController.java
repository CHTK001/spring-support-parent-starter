package com.chua.starter.config.server.support.controller;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.entity.ConfigurationApplicationInfo;
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
 * 配置中心应用
 *
 * @author CH
 * @since 2022/8/1 14:54
 */
@RequestMapping("/v1/app")
@RestController
public class ConfigurationApplicationController implements ApplicationContextAware {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private ConfigServerProperties configServerProperties;

    private DataManager dataManager;

    @Resource
    private RestTemplate restTemplate;

    /**
     * 配置頁面
     *
     * @return 頁面
     */
    @GetMapping("/command")
    @ResponseBody
    @SuppressWarnings("ALL")
    public ReturnPageResult<JSONObject> command(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "command") String command,
            @RequestParam(value = "method") String method,
    ) {


        return ReturnPageResult.ok();
    }

    /**
     * 配置頁面
     *
     * @return 頁面
     */
    @GetMapping("/page")
    @ResponseBody
    @SuppressWarnings("ALL")
    public ReturnPageResult<ConfigurationApplicationInfo> configList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "profile", required = false) String profile
    ) {

        Page infoPage = dataManager.findAll(ConfigConstant.APP, page - 1, pageSize, profile);
        return ReturnPageResult.ok(ReturnPageResult.<ConfigurationApplicationInfo>newBuilder()
                .data(infoPage.getContent()).page(page).pageSize(pageSize).totalPages(infoPage.getTotalPages()).total(infoPage.getTotalElements()).build());
    }

    /**
     * 配置頁面
     *
     * @return 頁面
     */
    @PostMapping("/save")
    @ResponseBody
    public ReturnResult<ConfigurationApplicationInfo> configSave(@RequestBody ConfigurationApplicationInfo configValue) {
        dataManager.save(ConfigConstant.APP, configValue);
        return ReturnResult.ok(configValue);
    }

    /**
     * 刪除頁面
     *
     * @return 頁面
     */
    @DeleteMapping("/delete")
    public ReturnResult<Void> configSave(String configId) {
        dataManager.delete(ConfigConstant.APP, configId);
        return ReturnResult.ok();
    }

    /**
     * 获取脚本
     *
     * @return 获取脚本
     */
    @GetMapping("/detail")
    public ReturnResult<Object> detail(String configId) {
        return ReturnResult.ok(dataManager.getDetail(ConfigConstant.APP, configId));
    }

    /**
     * 修改脚本
     *
     * @return 修改脚本
     */
    @PostMapping("/detailUpdate")
    public ReturnResult<Object> detailUpdate(@RequestBody DetailUpdate detailUpdate) {
        Object update = dataManager.detailUpdate(ConfigConstant.APP, detailUpdate);
        if (null != update) {
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
