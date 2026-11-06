package com.chua.starter.collect.configuration;

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 静态资源 MIME 类型补充配置。
 *
 * <p>本模块把前端 SPA 交给同一个进程托管（{@code /static/**} 资源处理器指向
 * {@code file:./static/}），SPA 启动时要用 fetch 读取运行时配置
 * {@code apps/{appName}/app.yaml}。前端 loader 对该响应做了严格校验：Content-Type
 * 必须是 yaml-like，否则直接丢弃配置、退回默认值——这条校验是刻意加的，用来防止
 * 把 SPA fallback 返回的 HTML 当 YAML 解析。</p>
 *
 * <p>问题在于 Tomcat 默认的 MIME 映射表里没有 {@code .yaml} / {@code .yml}，实测会返回
 * {@code application/octet-stream}，于是配置被静默丢弃：应用不抛任何异常，只是所有请求
 * 都按空 baseUrl 打到站点根路径，页面永远停在加载动画上，极难定位。这里补上映射返回
 * {@code text/yaml}（前端接受 yaml / yml / x-yaml / text/plain / 空这几种）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Configuration
public class StaticResourceMimeConfiguration {

    /**
     * 追加 yaml 相关的 MIME 映射。
     *
     * <p>基于 {@link MimeMappings#DEFAULT} 复制一份再追加，避免覆盖 Tomcat 自带的映射
     * （例如 {@code .wasm} 已经是 {@code application/wasm}，丢掉会连带影响 WebAssembly 加载）。</p>
     *
     * @return Tomcat 定制器
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> yamlMimeMappings() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("yaml", "text/yaml");
            mappings.add("yml", "text/yaml");
            factory.setMimeMappings(mappings);
        };
    }
}
