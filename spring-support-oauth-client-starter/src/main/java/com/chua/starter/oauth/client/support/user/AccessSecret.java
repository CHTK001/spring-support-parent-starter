package com.chua.starter.oauth.client.support.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * ak/sk
 *
 * @author CH
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessSecret {

    /**
     * ak
     */
    private String accessKey;

    /**
     * sk
     */
    private String secretKey;
    /**
     * key
     */
    private String uKey;

    private String password;

    private Map ext;

}
