package com.chua.starter.monitor.support.model;

/**
 * 挂载 / 卸载请求。
 *
 * @param source 设备或卷标识（卸载时可为空）
 * @param target 挂载点（Linux 目录 / Windows 盘符）
 * @param fsType 文件系统类型，可为空由系统自动识别
 * @author CH
 * @since 4.0.0.51
 */
public record MountActionVO(String source, String target, String fsType) {
}
