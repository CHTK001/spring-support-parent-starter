package com.chua.starter.device.support.model;

/**
 * 凭据连通性探测结果
 *
 * <p>「全部检测」一次返回每套凭据的探测结果，前端据此渲染凭据表的健康列与顶部汇总，
 * 不必再按凭据逐条发请求。</p>
 *
 * @param credentialId 凭据 ID
 * @param name         凭据名称
 * @param vendor       厂商标识
 * @param ok           是否连通
 * @param elapsedMs    本次探测耗时（毫秒）
 * @param message      失败原因；成功时为 {@code null}
 * @since 4.0.0.42
 * @author CH
 */
public record CredentialHealthResult(
        String credentialId,
        String name,
        String vendor,
        boolean ok,
        long elapsedMs,
        String message) {
}
