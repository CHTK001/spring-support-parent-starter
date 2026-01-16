package com.chua.starter.mybatis.reloader;

import org.springframework.beans.factory.InitializingBean;

import java.util.List;

/**
 * Mapper XML文件热重载接口
 * <p>
 * 提供Mapper XML文件的热重载功能，支持在运行时重新加载Mapper配置。
 * 实现类需要实现InitializingBean接口，在初始化时加载所有Mapper资源。
 * </p>
 *
 * @author CH
 */
public interface Reload extends InitializingBean {

    /**
     * 重载指定的Mapper XML文件
     * <p>
     * 根据文件名或路径查找对应的Mapper XML资源，重新解析并加载到MyBatis配置中。
     * 支持文件名（如：UserMapper.xml）或完整路径匹配。
     * </p>
     *
     * @param mapperXml Mapper XML文件名或路径
     * @return 重载结果信息，成功返回"重载成功: 文件名"，失败返回错误信息
     */
    String reload(String mapperXml);

    /**
     * 获取所有可重载的文件列表
     * <p>
     * 返回所有已加载的Mapper XML文件信息，包括文件名、路径、资源类型和是否可监听。
     * </p>
     *
     * @return 文件信息列表，包含文件名、路径、类型和监听状态
     */
    List<FileInfo> listFiles();
}
