package com.chua.starter.unified.client.support.configuration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.ProtocolServer;
import com.chua.common.support.protocol.server.annotations.ServiceMapping;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.unified.client.support.patch.PatchResolver;
import com.chua.starter.unified.client.support.properties.UnifiedClientProperties;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.Resource;

/**
 * OSHI配置
 *
 * @author CH
 */
public class UnifiedOptionConfiguration implements ApplicationContextAware {

    ProtocolServer protocolServer;

    @Resource
    private UnifiedClientProperties unifiedClientProperties;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        try {
            protocolServer = applicationContext.getBean(ProtocolServer.class);
            protocolServer.addMapping(this);
        } catch (BeansException ignored) {
        }
    }


    /**
     * 获取当前服务端配置
     *
     * @param request 请求
     * @return {@link BootResponse}
     */
    @ServiceMapping("oshi")
    public BootResponse oshi(BootRequest request ) {
        return BootResponse.ok();
    }

    /**
     * 补丁
     *
     * @param request 请求
     * @return {@link BootResponse}
     */
    @ServiceMapping("patch")
    public BootResponse patch(BootRequest request ) {
        String content = request.getContent();
        if(StringUtils.isBlank(content)) {
            return BootResponse.empty();
        }
        JSONObject jsonObject = JSON.parseObject(content);
        String patchFile = jsonObject.getString("patchFile");
        String patchFileName = jsonObject.getString("patchFileName");
        if(StringUtils.isBlank(patchFile) || StringUtils.isBlank(patchFileName)) {
            return BootResponse.empty();
        }

        PatchResolver patchResolver = new PatchResolver(patchFileName, unifiedClientProperties);
        patchResolver.resolve(patchFile);
        return BootResponse.ok();
    }

}
