package com.chua.starter.datasourcehub.support.model;

/**
 * JDBC 驱动 jar 视图。
 *
 * @param id              驱动 jar ID
 * @param jarName         上传时的原始文件名
 * @param storedName      落盘文件名
 * @param driverClassName 驱动类全名
 * @param jarSize         字节数
 * @param jarSha256       内容 sha256
 * @param remark          备注
 * @param createTime      上传时间
 * @author CH
 * @since 4.0.0.43
 */
public record JdbcDriverJarVO(
        Long id,
        String jarName,
        String storedName,
        String driverClassName,
        Long jarSize,
        String jarSha256,
        String remark,
        String createTime) {
}