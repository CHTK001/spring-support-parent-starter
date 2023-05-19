package com.chua.starter.oauth.server.support.generation;

import com.chua.common.support.annotations.Extension;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * 令牌生成器
 *
 * @author CH
 */
@Extension("simple")
public class SimpleTokenGeneration implements TokenGeneration {
    @Override
    public String generation(String key) {
        return DigestUtils.sha512Hex(DigestUtils.md5Hex(key));
    }
}
