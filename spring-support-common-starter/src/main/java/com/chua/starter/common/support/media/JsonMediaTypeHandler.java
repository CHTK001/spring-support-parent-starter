package com.chua.starter.common.support.media;

import com.chua.common.support.core.annotation.Extension;
import com.chua.common.support.text.json.Json;

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

