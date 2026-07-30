package com.chua.starter.common.support.media;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.spi.annotations.Extension;

/**
 * @author CH
 */
@Extension("json")
public class JsonMediaTypeHandler implements MediaTypeHandler {
    @Override
    public byte[] asByteArray(Object o) {
        return Json.toJSONBytes(o);
    }
}
