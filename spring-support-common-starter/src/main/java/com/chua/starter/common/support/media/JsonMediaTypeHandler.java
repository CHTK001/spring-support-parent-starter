package com.chua.starter.common.support.media;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.annotations.Extension;

/**
 * @author CH
 */
@Extension("json")
public class JsonMediaTypeHandler implements MediaTypeHandler {
    @Override
    public byte[] asByteArray(Object o) {
        return JSON.toJSONBytes(o);
    }
}
