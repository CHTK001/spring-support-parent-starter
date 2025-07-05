package com.chua.starter.oauth.client.support.user;

import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.springframework.util.ReflectionUtils;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * 用户信息
 *
 * @author CH
 * @since 2022/7/23 8:48
 */
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
@JsonIgnoreProperties({"beanType", "accessSecret", "expire", "password", "salt", "password", "salt", "userEnable", "address", "lastArea", "lastLatitude", "lastIp", "lastLongitude"})
public class UserResult extends UserResume implements Serializable {


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
