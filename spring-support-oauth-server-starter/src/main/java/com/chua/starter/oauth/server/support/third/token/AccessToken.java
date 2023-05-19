package com.chua.starter.oauth.server.support.third.token;

import lombok.Data;

/**
 * token
 *
 * @author CH
 */
@Data
public class AccessToken {
    /**
     * token
     */
    private String accessToken;
    /**
     * expire
     */
    private Integer expiresIn;
}
