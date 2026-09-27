package com.chua.starter.oauth.client.support.contants;

import com.chua.common.support.constant.CaptchaConstant;

import java.util.Set;

/**
 * 认证常量
 *
 * @author CH
 * @since 4.0.0
 */
public class AuthConstant {

    /**
     * 父_admin
    */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * Admin
    */
    public static final String ADMIN = "ADMIN";

    /**
     * Ops
    */
    public static final String OPS = "OPS";

    /**
     * 内置超级管理员账号名（与 sys_user 种子一致，拥有全部权限与菜单，角色不可变更）
     */
    public static final String BUILTIN_ADMIN_USERNAME = "admin";

    /**
     * 验证码会话 Key
     *
     * <p>必须与写入方 {@code CaptchaProvider} 保持一致，直接引用
     * {@link CaptchaConstant#CAPTCHA_SESSION_KEY}，避免两处各写一个字符串常量
     * 导致读不到、写进去的验证码永远校验不通过。</p>
     */
    public static final String CAPTCHA_SESSION_KEY = CaptchaConstant.CAPTCHA_SESSION_KEY;

    /**
     * 访问令牌 Redis 键前缀
     */
    public static final String TOKEN_PRE = "oauth:token:";

    /**
     * 刷新令牌 Redis 键前缀
     */
    public static final String TOKEN_REFRESH_PRE = "oauth:refresh:";

    /**
     * 是否父admin
     *
     * @param roles 角色
     * @return 是否父admin的结果
     */
    public static boolean isSuperAdmin(Set<String> roles) {
        return roles != null && roles.stream().anyMatch(SUPER_ADMIN::equalsIgnoreCase);
    }

    /**
     * 是否Admin
     *
     * <p>仅用于"是否具备管理员这一能力"的判断（如能否管理他人资料、能否改动内置数据），
     * 不代表全量放行；全量放行请用 {@link #isPrivileged(String, Set)}。</p>
     *
     * @param roles 角色
     * @return 是否admin的结果
     */
    public static boolean isAdmin(Set<String> roles) {
        return roles != null && roles.stream()
                .anyMatch(code -> SUPER_ADMIN.equalsIgnoreCase(code) || "ADMIN".equalsIgnoreCase(code));
    }

    /**
     * 是否内置 admin 账号
     *
     * <p>账号级身份，与"被授予 ADMIN 角色"是两回事：ADMIN 角色可以由菜单授权收窄，
     * 内置账号则始终拥有全部权限与菜单，因此判权必须以账号为准。</p>
     *
     * @param username 登录账号
     * @return 是否内置 admin 账号
     */
    public static boolean isBuiltinAdmin(String username) {
        return BUILTIN_ADMIN_USERNAME.equals(username);
    }

    /**
     * 是否特权账号：内置 admin 账号或持有超管角色
     *
     * @param username 登录账号
     * @param roles    角色编码集合
     * @return true 表示拥有全部权限与菜单
     */
    public static boolean isPrivileged(String username, Set<String> roles) {
        return isBuiltinAdmin(username) || isSuperAdmin(roles);
    }
}
