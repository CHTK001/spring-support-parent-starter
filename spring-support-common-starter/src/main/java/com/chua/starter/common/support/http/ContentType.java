package com.chua.starter.common.support.http;

/**
 * HTTP Content-Type 描述。
 * @author CH
 */
public class ContentType {

    /** Mime类型 */
    private final String mimeType;

    /**
     * 创建 ContentType 实例
     * @param mimeType mimeType
     */
    public ContentType(String mimeType) {
        this.mimeType = mimeType;
    }

    /** 获取MimeType */
    public String getMimeType() {
        return mimeType;
    }
}

