package com.chua.starter.monitor.support.model;

/**
 * 挂载 / 卸载请求。
 *
 * <p>挂载需要设备与挂载点；卸载只需要挂载点，未使用的字段留空即可。
 * 服务端对挂载点与设备做控制字符清洗，命令文本由平台能力集生成。</p>
 *
 * @param source 设备 / 卷标识（Linux {@code /dev/sdb1}、Windows 盘符），卸载时可为空
 * @param target 挂载点（Linux 目录、Windows 盘符）
 * @param fsType 文件系统类型（可为空，由目标机自动识别）
 * @author CH
 * @since 4.0.0.52
 */
public record MountRequestVO(String source, String target, String fsType) {
}
