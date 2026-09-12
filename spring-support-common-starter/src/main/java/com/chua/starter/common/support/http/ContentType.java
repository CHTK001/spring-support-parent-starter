package com.chua.starter.common.support.http;

/**
* HTTP 内容-类型 描述。
* @author CH
* @since 4.0.0
 */
public class ContentType {

    /** Mime类型 */
    private final String mimeType;

    /**
    * 创建 内容类型 实例
    * @param mimeType mime类型
     */
    public ContentType(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
    * 获取mime类型
    *
    * @return 获取mime类型的结果
     */
    public String getMimeType() {
        return mimeType;
    }
}

