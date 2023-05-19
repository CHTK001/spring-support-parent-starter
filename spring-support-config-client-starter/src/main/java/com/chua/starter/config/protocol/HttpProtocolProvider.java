package com.chua.starter.config.protocol;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.crypto.Encrypt;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.config.entity.KeyValue;
import com.google.common.base.Strings;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 协议
 *
 * @author CH
 * @since 2022/7/30 12:07
 */
@Slf4j
@RestController
@Spi("http")
public class HttpProtocolProvider extends AbstractProtocolProvider {


    @Override
    public String[] named() {
        return new String[]{"http"};
    }

    @Override
    protected String send(String encode) {
        HttpResponse<String> response = null;
        try {
            response = Unirest.post(named()[0] + "://" + configProperties.getConfigAddress().concat("/config/registerBean"))
                    .field("data", encode)
                    .field("binder", configProperties.getConfigName())
                    .asString();
        } catch (Throwable e) {
            log.warn(e.getMessage());
            return null;
        }

        if(null == response || response.getStatus() != 200) {
            return null;
        }

        return response.getBody();
    }

    @Override
    protected String sendDestroy(String encode) {
        HttpResponse<String> response = null;
        try {
            response = Unirest.post(named()[0] + "://" + configProperties.getConfigAddress().concat("/config/unregister"))
                    .field("data", encode)
                    .field("binder", configProperties.getConfigName())
                    .asString();
        } catch (Throwable e) {
            log.warn(e.getMessage());
        }

        if (null == response || response.getStatus() != 200) {
            log.error("注册中心注销失败");
            return null;
        }

        return "success";
    }


    @PostMapping("/config/listener")
    @Override
    public void listener(String data) {
        //服务端主动发起信息
        Encrypt provider = ServiceProvider.of(Encrypt.class).getExtension(configProperties.getEncrypt());
        String decode = provider.decodeHex(data, StringUtils.defaultString(configProperties.getKey(), DEFAULT_SER));
        if (Strings.isNullOrEmpty(decode)) {
            log.warn("参数无法解析");
            return;
        }

        KeyValue keyValue = JSON.parseObject(decode, KeyValue.class);
        if (null == keyValue) {
            log.warn("参数无法解析");
            return;
        }

        log.info("配置{} -> {}", keyValue.getDataId(), keyValue.getData());
        configValueAnnotationBeanPostProcessor.onChange(keyValue);
    }

}
