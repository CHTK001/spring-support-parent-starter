package com.chua.starter.ai.support.manage.limit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 限制探测上下文：把「测谁」所需的最小信息集中传入，避免各实现重复拼装。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderLimitContext {

    /**
     * 平台类型（openai / deepseek / moss ...）
     */
    private String platform;

    /**
     * 上游基础地址
     */
    private String baseUrl;

    /**
     * 服务商 API Key（可为空；限制探测不额外发请求，只用于标识来源同源性）
     */
    private String apiKey;

    /**
     * 鉴权方案（api_key / bearer / oauth / none）
     */
    private String authScheme;

    /**
     * 自定义认证头名
     */
    private String authHeader;

    /**
     * 自定义认证前缀
     */
    private String authPrefix;

    /**
     * 自定义请求头
     */
    private Map<String, String> extraHeaders;

    /**
     * HTTP 代理地址
     */
    private String proxy;

    /**
     * 服务商扩展配置（{@code extra}）
     */
    private Map<String, Object> extra;

    /**
     * 本次调用上游 {@code /models} 的响应头（键大小写不敏感）
     */
    private Map<String, List<String>> responseHeaders;
}
