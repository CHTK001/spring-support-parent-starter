package com.chua.starter.common.support.http;

/**
 * HTTP Content-Type 描述。
 * @author CH
 */
public class ContentType {

    /** Mime类型 */
    private final String mimeType;

    public ContentType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }
}

