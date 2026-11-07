package com.chua.starter.server.support.entity;

/**
 * 软件包信息
 *
 * @param name    软件包名称
 * @param summary 软件包描述
 * @param version 版本
 * @author CH
 * @since 4.0.0.51
 */
public record SoftwarePackage(
        String name,
        String summary,
        String version) {
}
