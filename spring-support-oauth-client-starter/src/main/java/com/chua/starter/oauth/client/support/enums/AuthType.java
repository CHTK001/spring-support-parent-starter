
package com.chua.starter.oauth.client.support.enums;

/**
 * 认证类型枚举
 * <p>定义系统支持的认证方式</p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2022/7/26 8:20
 */
public enum AuthType {

    /**
     * Web登录认证
     * <p>基于用户名/密码的标准Web登录方式</p>
     */
    WEB("web", "Web登录"),

    /**
     * Token认证
     * <p>基于Token的认证方式，Token存在于sys_token表中即为有效</p>
     */
    TOKEN("token", "Token认证"),

    /**
     * 微信认证
     * <p>微信相关的认证方式，包括微信小程序、公众号等</p>
     */
    WECHAT("wechat", "微信认证"),

    /**
     * 租户认证
     * <p>多租户系统的租户级别认证</p>
     */
    TENANT("tenant", "租户认证"),

    /**
     * 无认证类型
     * <p>用于默认值或未指定认证类型的场景</p>
     */
    NONE("none", "无");

    /**
     * 认证类型编码
     */
    private final String code;

    /**
     * 认证类型描述
     */
    private final String description;

    /**
     * 构造函数
     *
     * @param code        认证类型编码
     * @param description 认证类型描述
     */
    AuthType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取认证类型编码
     *
     * @return 认证类型编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取认证类型描述
     *
     * @return 认证类型描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据编码获取认证类型
     *
     * @param code 认证类型编码
     * @return 认证类型枚举，未找到返回 NONE
     */
    public static AuthType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return NONE;
        }
        for (AuthType type : values()) {
            if (type.code.equalsIgnoreCase(code) || type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return NONE;
    }
}
