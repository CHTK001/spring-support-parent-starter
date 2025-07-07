package com.chua.starter.oauth.client.support.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 用户信息
 *
 * @author CH
 * @since 2022/7/23 8:48
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@NoArgsConstructor
@JsonIgnoreProperties({"beanType", "accessSecret", "expire", "password", "salt", "password", "salt", "userEnable", "lastArea", "lastLatitude", "lastIp", "lastLongitude"})
public class UserResult extends UserResume implements Serializable {

    public UserResult(String message) {
        super(message);
    }

    /**
     * 令牌
     */
    private String token;
    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 过期时间
     */
    private Long refreshExpireTime;


}
