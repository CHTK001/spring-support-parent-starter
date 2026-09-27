package com.chua.starter.monitor.support.model;

/**
 * 主机文件/目录权限视图（类 Unix）。
 *
 * @param mode  八进制权限，如 755
 * @param owner 属主
 * @param group 属组
 * @author CH
 * @since 4.0.0.42
 */
public record FilePermVO(String mode, String owner, String group) {
}
