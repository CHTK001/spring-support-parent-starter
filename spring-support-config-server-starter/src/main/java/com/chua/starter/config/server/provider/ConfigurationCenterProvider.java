package com.chua.starter.config.server.provider;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.config.server.pojo.ConfigurationCenterInfo;
import com.chua.starter.config.server.properties.ConfigServerProperties;
import com.chua.starter.config.server.protocol.ProtocolServer;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 配置中心
 * @author CH
 * @since 2022/8/1 14:54
 */
@RequestMapping("${plugin.configuration.server.context-path:}")
@RestController
public class ConfigurationCenterProvider implements ApplicationContextAware {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private ConfigServerProperties configServerProperties;

    private ProtocolServer protocolServer;
    /**
     * 配置頁面
     * @return 頁面
     */
    @GetMapping("/config-list")
    @ResponseBody
    public ReturnPageResult<ConfigurationCenterInfo> configList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "profile", defaultValue = "dev") String profile
    ) {

        Page<ConfigurationCenterInfo> infoPage = protocolServer.findAll(page - 1, pageSize, profile);
        return ReturnPageResult.ok(ReturnPageResult.<ConfigurationCenterInfo>newBuilder()
                .data(infoPage.getContent()).page(page).pageSize(pageSize).totalPages(infoPage.getTotalPages()).total(infoPage.getTotalElements()).build());
    }


    /**
     * 配置頁面
     * @return 頁面
     */
    @PostMapping("/config-update")
    @ResponseBody
    public ReturnResult<Void> configUpdate(
            @RequestParam(value = "config-id", defaultValue = "") Integer configId,
            @RequestParam(value = "config-value", defaultValue = "") String configValue,
            @RequestParam(value = "config-disable", defaultValue = "") Integer disable
    ) {

        protocolServer.notifyConfig(configId, configValue, disable, null);
        return ReturnResult.ok();
    }
    /**
     * 配置頁面
     * @return 頁面
     */
    @PostMapping("/distribute-update")
    @ResponseBody
    public ReturnResult<Void> distributeUpdate(
            @RequestParam(value = "config-id") List<Integer> configId,
            @RequestParam(value = "config-item", defaultValue = "") String configItem
    ) {
        protocolServer.distributeUpdate(configId, configItem);
        return ReturnResult.ok();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        protocolServer = applicationContext.getBean(StringUtils.defaultString(configServerProperties.getProtocol(), ConfigServerProperties.DEFAULT_PROTOCOL), ProtocolServer.class);
    }
}
