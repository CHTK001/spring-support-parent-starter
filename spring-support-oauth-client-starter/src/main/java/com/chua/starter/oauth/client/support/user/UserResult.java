package com.chua.starter.oauth.client.support.user;

import com.chua.common.support.lang.json.Json;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 用户认证结果
 * <p>
 * 继承自 {@link UserResume}，在用户基本信息基础上增加Token相关字段。
 * 主要用于登录成功后返回给客户端的完整用户信息。
 * </p>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>登录成功后返回用户信息和Token</li>
 *   <li>刷新Token时返回新的Token</li>
 *   <li>Token验证后获取用户信息</li>
 * </ul>
 *
 * <h3>Token类型：</h3>
 * <ul>
 *   <li><b>token</b> - 访问令牌(AT)，用于API认证，有效期较短（默认3天）</li>
 *   <li><b>refreshToken</b> - 刷新令牌(RT)，用于获取新的访问令牌，有效期较长（默认4天）</li>
 * </ul>
 *
 * @version 1.0.0
 * @since 2022/7/23 8:48
 * @see UserResume
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@NoArgsConstructor
@Schema(description = "用户认证结果")
@JsonIgnoreProperties({"beanType", "accessSecret", "expire", "password", "salt", "userEnable", "lastArea", "lastLatitude", "lastLongitude"})
public class UserResult extends UserResume implements Serializable {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;
    /** _empty */
    private static final UserResult _EMPTY = new UserResult();

    /**
     * 构造函数（用于创建错误消息）
     *
     * @param message 错误消息
     */
    public UserResult(String message) {
        super(message);
    }

    // ==================== Token信息 ====================

    /**
     * 访问令牌
     * <p>用于API认证，每次请求需携带此Token</p>
     * <p>格式：AT{uid}{randomHex}，前缀AT表示Access Token</p>
     */
    @Schema(description = "访问令牌")
    /** 令牌 */
    private String token;

    /**
     * 刷新令牌
     * <p>用于获取新的访问令牌，当访问令牌过期时使用</p>
     * <p>格式：RT{uid}{randomHex}，前缀RT表示Refresh Token</p>
     */
    @Schema(description = "刷新令牌")
    /** Refresh令牌 */
    private String refreshToken;

    /**
     * 刷新令牌过期时间（秒）
     * <p>刷新令牌失效的时间戳</p>
     */
    @Schema(description = "刷新令牌过期时间（秒）")
    /** Refreshexpire时间 */
    private Long refreshExpireTime;

    /**
     * 登录类型
     */
    private String loginType;

    /**
     * 用户UID
     */
    private String uid;

    /**
     * openId
     */
    private String openId;

    /**
     * unionId
     */
    private String unionId;

    /**
     * 过期时间（秒）
     */
    private Long expireTime;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 地址
     */
    private String address;

    /**
     * 获取空用户结果
     *
     * @return 空用户结果
     */
    public static UserResult emptyUserResult() {
        return _EMPTY;
    }

    /**
     * 从用户简历转换用户认证结果
     * <p>UserResult 与 UserResume 均使用 {@link Accessors @Accessors(chain = true)}，
     * setter 返回自身而非 void，JavaBeans Introspector 无法识别其 write method，
     * 导致 {@link com.chua.common.support.utils.BeanUtils#copyProperties(Object, Object)} 复制时字段全部丢失。
     * 这里改用 JSON 序列化/反序列化完成转换，保证继承自 UserResume 的字段完整保留。</p>
     *
     * @param userResume 用户简历
     * @return 用户认证结果
     */
    public static UserResult fromResume(UserResume userResume) {
        if (null == userResume) {
            return null;
        }
        if (userResume instanceof UserResult userResult) {
            return userResult;
        }
        return Json.fromJson(Json.toJson(userResume), UserResult.class);
    }
}

