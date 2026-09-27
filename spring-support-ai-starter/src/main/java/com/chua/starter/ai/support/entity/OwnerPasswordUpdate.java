package com.chua.starter.ai.support.entity;

import lombok.Data;

/**
 * 账号安全 - 修改密码请求体。
 *
 * <p>字段与系统体系（system-starter 的 {@code SysUserPasswordUpdate}）保持一致：
 * {@code oldPassword/newPassword/confirmPassword}，统一 camelCase。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class OwnerPasswordUpdate {

    /**
     * 原密码
     */
    private String oldPassword;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认新密码
     */
    private String confirmPassword;
}
