package com.chua.starter.oauth.client.support.user;

/**
 * 已通过第一步身份校验的短期挑战，不具有业务接口访问权限。
 * @param challengeToken 一次性不透明凭证
 * @param channel 验证渠道，当前仅 SMS
 * @param maskedTarget 脱敏目标
 * @param expiresAt 失效时间，Unix 毫秒
 * @author CH
 */
public record SecondFactorChallenge(String challengeToken, String channel, String maskedTarget, long expiresAt) {
}
