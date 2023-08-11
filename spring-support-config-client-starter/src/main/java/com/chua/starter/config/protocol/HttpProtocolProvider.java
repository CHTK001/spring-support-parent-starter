package com.chua.starter.config.protocol;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.config.entity.KeyValue;
import com.google.common.base.Strings;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
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
public class HttpProtocolProvider extends AbstractProtocolProvider implements Handler<HttpServerRequest> {

    private final Vertx vertx = Vertx.vertx();
    private HttpServer httpServer;

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
                    .field("binder", applicationName)
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
                    .field("binder", applicationName)
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

    @Override
    protected void start() {
        this.httpServer = vertx.createHttpServer();
        this.httpServer.requestHandler(this);
        this.httpServer.listen(getPort(), getHost());
    }


    @Override
    public void handle(HttpServerRequest request) {
        String uri = request.uri();
        if (!"/config/listener".equals(uri)) {
            HttpServerResponse response = request.response();
            //设置响应头
            response.putHeader("Content-type", "application/json;charset=utf-8");
            // 响应数据
            response.end("不支持");
            return;
        }

        request.body(new Handler<AsyncResult<Buffer>>() {
            @Override
            public void handle(AsyncResult<Buffer> event) {
                String data = event.result().toString();
                //服务端主动发起信息
                Codec provider = ServiceProvider.of(Codec.class).getExtension(configProperties.getEncrypt());
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
        });

    }

    @Override
    public void listener(String data) {

    }

    @Override
    public void close() throws Exception {
        try {
            this.httpServer.close();
        } catch (Exception ignored) {
        }
        try {
            vertx.close();
        } catch (Exception ignored) {
        }
    }

}
