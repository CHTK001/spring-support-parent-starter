package com.chua.starter.proxy.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.proxy.support.entity.SystemServer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 系统服务器配置 Mapper 接口
 *
 * @author CH
 * @since 2025/01/07
 */
@Mapper
public interface SystemServerMapper extends BaseMapper<SystemServer> {

    /**
     * 分页查询服务器列表
     *
     * @param page   分页参数
     * @param entity 查询条件
     * @return 分页结果
     */
    IPage<SystemServer> pageFor(Page<SystemServer> page, @Param("entity") SystemServer entity);

    /**
     * 根据服务器类型查询服务器列表
     *
     * @param serverType 服务器类型
     * @return 服务器列表
     */
    List<SystemServer> selectByServerType(@Param("serverType") String serverType);

    /**
     * 根据状态查询服务器列表
     *
     * @param status 服务器状态
     * @return 服务器列表
     */
    List<SystemServer> selectByStatus(@Param("status") String status);

    /**
     * 获取服务器统计信息
     *
     * @return 统计信息
     */
    Map<String, Object> getStatistics();

    /**
     * 根据端口查询服务器
     *
     * @param port 端口号
     * @return 服务器信息
     */
    SystemServer selectByPort(@Param("port") Integer port);

    /**
     * 更新服务器状态
     *
     * @param serverId 服务器ID
     * @param status   新状态
     * @return 更新结果
     */
    int updateStatus(@Param("serverId") Integer serverId, @Param("status") String status);
}




