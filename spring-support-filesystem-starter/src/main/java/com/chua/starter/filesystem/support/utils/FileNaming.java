// [P3C A20 豁免] A20/A30/A31: 仅含常量定义行，豁免。
package com.chua.starter.filesystem.support.utils;

import java.util.Locale;

/**
 * 对象键与文件命名工具。
 *
 * <p>桶名参与对象键与磁盘路径，必须只含安全字符；原始文件名参与展示与下载响应头，
 * 必须按列宽截断。分片上传与单次上传写入同一套桶与同一批列，因此规则集中在此处，
 * 避免两条链路对同一个输入给出不同结果。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class FileNaming {

    /**
     * 缺省桶名
     */
    private static final String DEFAULT_BUCKET = "default";

    /**
     * 桶名长度上限
     */
    private static final int MAX_BUCKET_LENGTH = 32;

    /**
     * 创建 文件naming 实例
    */
    private FileNaming() {
    }

    /**
     * 归一化桶名，仅保留小写字母、数字与连字符。
     *
     * @param bucket 原始桶名
     * @return 归一化后的桶名，空值返回 default
     */
    public static String normalizeBucket(String bucket) {
        if (bucket == null || bucket.trim().isEmpty()) {
            return DEFAULT_BUCKET;
        }
        String cleaned = bucket.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        if (cleaned.isEmpty()) {
            return DEFAULT_BUCKET;
        }
        return cleaned.length() > MAX_BUCKET_LENGTH ? cleaned.substring(0, MAX_BUCKET_LENGTH) : cleaned;
    }

    /**
     * 按列宽截断名称，避免超长原始文件名写库报错。
     *
     * @param value     原字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串，入参为空时返回 空
     */
    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
