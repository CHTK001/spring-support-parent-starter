package com.chua.starter.server.support.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SIP 隧道配置
 * <p>
 * 读写共用：出参带 {@code running} 运行态；入参可省略 {@code running}（该值由服务端按
 * 启停结果决定，不接受客户端覆盖）。{@code enabled} 沿用配置表的字符串语义（"true"/"false"），
 * 未识别字段忽略。
 * </p>
 *
 * @param enabled     是否启用中继（配置键 sip.enabled）
 * @param controlPort 控制端口（配置键 sip.control.port）
 * @param secret      共享口令（配置键 sip.secret）
 * @param serverHost  中继服务器地址（配置键 sip.server.host）
 * @param running     中继服务当前是否在跑
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SipConfigVO(
        @JsonProperty("sip.enabled") String enabled,
        @JsonProperty("sip.control.port") Integer controlPort,
        @JsonProperty("sip.secret") String secret,
        @JsonProperty("sip.server.host") String serverHost,
        @JsonProperty("running") boolean running
) {
}
