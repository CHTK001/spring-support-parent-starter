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

    /**
     * 创建 ContentDisposition 实例
     * @param filename filename
     * @param String String
     * @param String String
     */
    public ContentDisposition(String filename, String charset, String name) {
        this.filename = filename;
        this.charset = charset;
        this.name = name;
    }

    /** 获取Filename */
    public String getFilename() {
        return filename;
    }

    /** 获取Charset */
    public String getCharset() {
        return charset;
    }

    /** 获取Name */
    public String getName() {
        return name;
    }
}

