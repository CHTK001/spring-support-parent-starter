package com.chua.starter.datasearch.provider.limit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 限制探测上下文：把「测谁」所需的最小信息集中传入，避免各机制实现重复拼装。
 *
 * <p>只承载传输层事实（地址、鉴权、代理、上次响应头）与宿主透传的 {@code extra}，
 * 因此本类不依赖任何宿主模块的存储模型：宿主把 {@code SysAiProvider} 的字段
 * 摊平进来即可，datasearch 不需要反过来依赖宿主的实体类。</p>
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
