
package com.chua.starter.oauth.client.support.enums;

/**
 * 认证类型枚举
 * <p>定义系统支持的认证方式</p>
 *
 * @since 2022/7/26 8:20
 * @author CH
 */
public enum AuthType {

    /**
     * Web登录认证
     * <p>基于用户名/密码的标准Web登录方式</p>
     * @param "web" "web"
     * @param "Web登录" "Web登录"
     * @return WEB的结果
     */
    WEB("web", "Web登录"),

    /**
     * 令牌认证
     * <p>基于Token的认证方式，Token存在于sys_token表中即为有效</p>
     * @param "token" "令牌"
     * @param "Token认证" "令牌认证"
     * @return 令牌的结果
     */
    TOKEN("token", "Token认证"),

    /**
     * 微信认证
     * <p>微信相关的认证方式，包括微信小程序、公众号等</p>
     * @param "wechat" "wechat"
     * @param "微信认证" "微信认证"
     * @return WECHAT的结果
     */
    WECHAT("wechat", "微信认证"),

    /**
     * 租户认证
     * <p>多租户系统的租户级别认证</p>
     * @param "tenant" "tenant"
     * @param "租户认证" "租户认证"
     * @return TENANT的结果
     */
    TENANT("tenant", "租户认证"),

    /**
     * Gitee认证
     * <p>Gitee第三方OAuth2.0登录</p>
     * @param "gitee" "gitee"
     * @param "Gitee认证" "Gitee认证"
     * @return GITEE的结果
     */
    GITEE("gitee", "Gitee认证"),

    /**
     * GitHub认证
     * <p>GitHub第三方OAuth2.0登录</p>
     * @param "github" "GitHub"
     * @param "GitHub认证" "GitHub认证"
     * @return GITHUB的结果
     */
    GITHUB("github", "GitHub认证"),

    /**
     * 第三方认证
     * <p>通用第三方OAuth2.0登录，用于Gitee、GitHub、微信等第三方平台</p>
     * @param "third_party" "third_party"
     * @param "第三方认证" "第三方认证"
     * @return THIRD_PARTY的结果
     */
    THIRD_PARTY("third_party", "第三方认证"),

    /**
     * 短信验证码认证
     * <p>基于手机号和短信验证码的登录方式</p>
     * @param "sms" "sms"
     * @param "短信验证码认证" "短信验证码认证"
     * @return SMS的结果
     */
    SMS("sms", "短信验证码认证"),

    /**
     * 邮箱验证码认证
     * <p>基于邮箱地址和邮箱验证码的登录方式，验证码已在业务层校验，令牌签发免口令</p>
     */
    EMAIL("email", "邮箱验证码认证"),

    /**
     * 学生登录
     * <p>面向教育域学生账号：登录标识为学号或家长手机号，
     * 由 OAuth 服务端的 {@code StudentLoginService} 校验（注册名 {@code STUDENT}）。
     * 与 WEB 的差别是账号体系不同——学生不是 sys_user，而是 edu_student。</p>
     * @param "student" "学生"
     * @param "学生登录" "学生登录"
     * @return 学生的结果
     */
    STUDENT("student", "学生登录"),

    /**
     * 教师登录
     * <p>面向教育域教师账号：登录标识为工号，凭据由业务模块注册的
     * {@code TeacherCredentialProvider}（注册名 {@code teacher}）校验。
     * 与 STUDENT 同形——教师也不是 sys_user，而是 edu_teacher。</p>
     *
     * @param "teacher" "教师"
     * @param "教师登录" "教师登录"
     * @return 教师的结果
     */
    TEACHER("teacher", "教师登录"),

    /**
     * 无认证类型
     * <p>用于默认值或未指定认证类型的场景</p>
     * @param "none" "无"
     * @param "无" "无"
     * @return 无的结果
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
     * @return 认证类型枚举，未找到返回 无
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
