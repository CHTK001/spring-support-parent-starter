package com.chua.starter.oauth.client.support.user;

/**
 * 宿主的登录完成策略。认证端在用户校验和令牌签发之前调用，不持有业务存储。
 * @author CH
 */
public interface LoginIssuancePolicy {
    /**
     * @param principal 登录标识
     * @param credential 登录凭证
     * @param authType 实际认证类型
     * @param ext 登录归因
     * @return 拒绝原因；null 表示允许继续凭据校验
     */
    String rejectReason(String principal, String credential, String authType, Object ext);
}
