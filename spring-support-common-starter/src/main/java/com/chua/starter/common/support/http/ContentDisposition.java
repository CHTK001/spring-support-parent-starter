package com.chua.starter.common.support.http;

/**
   * HTTP 内容-Disposition 描述。
 * @author CH
 * @since 4.0.0
 */
public class ContentDisposition {

    /** 文件名 */
    private final String filename;
    /** 字符集 */
    private final String charset;
    /** 名称 */
    private final String name;

    /**
      * 创建 内容disposition 实例
     * @param filename 文件名
     * @param filename 字符串
     * @param filename 字符串
     * @param charset 字符集
     * @param name 名称
     */
    public ContentDisposition(String filename, String charset, String name) {
        this.filename = filename;
        this.charset = charset;
        this.name = name;
    }

    /**
     * 获取文件名
     *
     * @return 获取文件名的结果
     */
    public String getFilename() {
        return filename;
    }

    /**
     * 获取字符集
     *
     * @return 获取字符集的结果
     */
    public String getCharset() {
        return charset;
    }

    /**
     * 获取名称
     *
     * @return 获取名称的结果
     */
    public String getName() {
        return name;
    }
}

