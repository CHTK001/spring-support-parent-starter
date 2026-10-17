package com.chua.starter.mybatis.reloader;

/**
 * 映射器 XML文件信息
 * <p>
 * 用于描述可重载的映射器 XML文件的详细信息，包括文件位置、资源类型和监听能力。
 * </p>
 *
 * @param name      文件名（不含路径）
 * @param path      文件完整路径或URI
 * @param type      资源类型（FILE/CLASSPATH/JAR/UNKNOWN）
 * @param watchable 是否可监听文件变化
 * @author CH
 * @since 4.0.0
 */
public record FileInfo(
        String name,
        String path,
        String type,
        boolean watchable) {
}
