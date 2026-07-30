package com.chua.starter.mybatis.reloader;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mapper XML文件信息
 * <p>
 * 用于描述可重载的Mapper XML文件的详细信息，包括文件位置、资源类型和监听能力。
 * </p>
 *
 * @author CH
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {
    /**
     * 文件名（不含路径）
     * <p>
     * 例如：UserMapper.xml
     * </p>
     */
    String name;

    /**
     * 文件完整路径或URI
     * <p>
     * 本地文件：file:///path/to/UserMapper.xml
     * classpath资源：classpath:mapper/UserMapper.xml
     * jar包资源：jar:file:/path/to.jar!/mapper/UserMapper.xml
     * </p>
     */
    String path;

    /**
     * 资源类型
     * <p>
     * FILE：本地文件系统中的文件，可监听
     * CLASSPATH：classpath中的资源，如果在文件系统中可监听
     * JAR：jar包或war包中的资源，不可监听
     * UNKNOWN：未知类型，不可监听
     * </p>
     */
    String type;

    /**
     * 是否可监听文件变化
     * <p>
     * true：文件在本地文件系统中，可以监听文件变化并自动重载
     * false：文件在jar包中或无法访问，无法监听
     * </p>
     */
    boolean watchable;

    /**
     * 是否可监听
     *
     * @return 是否可监听
     */
    public boolean isWatchable() {
        return watchable;
    }
}

