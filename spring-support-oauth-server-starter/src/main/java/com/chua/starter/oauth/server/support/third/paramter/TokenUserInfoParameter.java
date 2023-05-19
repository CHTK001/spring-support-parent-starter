package com.chua.starter.oauth.server.support.third.paramter;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * token
 *
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TokenUserInfoParameter extends BaseParameter {
    private String token;
}
