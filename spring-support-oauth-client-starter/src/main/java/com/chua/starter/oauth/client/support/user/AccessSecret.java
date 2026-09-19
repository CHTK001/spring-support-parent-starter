package com.chua.starter.oauth.client.support.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * ak/sk
 *
 * @author CH
 * @since 4.0.0
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
     * Key
     */
    private String uKey;

    /** 密码 */
    private String password;

    private Map<String, Object> ext; // ext

    /**
    * 缓存时间（用于性能优化）
    */
    private transient long cacheTime;

}
