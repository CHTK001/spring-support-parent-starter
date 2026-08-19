package com.chua.starter.common.support.http;

/**
 * HTTP Content-Disposition 描述。
 * @author CH
 */
public class ContentDisposition {

    /** Filename */
    private final String filename;
    /** 字符集 */
    private final String charset;
    /** 名称 */
    private final String name;

    public ContentDisposition(String filename, String charset, String name) {
        this.filename = filename;
        this.charset = charset;
        this.name = name;
    }

    public String getFilename() {
        return filename;
    }

    public String getCharset() {
        return charset;
    }

    public String getName() {
        return name;
    }
}

