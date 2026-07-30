package com.chua.starter.common.support.http;

/**
 * HTTP Content-Disposition 描述。
 */
public class ContentDisposition {

    private final String filename;
    private final String charset;
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
