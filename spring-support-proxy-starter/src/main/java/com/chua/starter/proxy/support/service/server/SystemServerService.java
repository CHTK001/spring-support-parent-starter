package com.chua.starter.proxy.support.service.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.protocol.server.ProtocolServer;
import com.chua.common.support.core.spi.SpiOption;
import com.chua.starter.proxy.support.entity.SystemServer;

import java.util.List;
import java.util.Map;

/**
 * 系统服务器配置服务接口
 *
 * @author CH
 * @since 2025/01/07
 */
public interface SystemServerService extends IService<SystemServer> {

    /**
     * 分页查询服务器列表
     *
     * @param page   分页参数
     * @param entity 查询条件
     * @return 分页结果
     */
    IPage<SystemServer> pageFor(Page<SystemServer> page, SystemServer entity);

    /**
     * 获取可用的服务器类型列表
     *
     * @return 服务器类型列表
     */
    ReturnResult<List<SpiOption>> getAvailableServerTypes();

    /**
     * 获取服务器统计信息
     *
     * @return 统计信息
     */
    ReturnResult<Map<String, Object>> getStatistics();

    /**
     * 启动服务器
     *
     * @param serverId 服务器ID
     * @return 启动结果
     */
    ReturnResult<Boolean> startServer(Integer serverId);

    /**
     * 停止服务器
     *
     * @param serverId 服务器ID
     * @return 停止结果
     */
    ReturnResult<Boolean> stopServer(Integer serverId);

    /**
     * 获取服务器状态
     *
     * @param serverId 服务器ID
     * @return 服务器状态
     */
    ReturnResult<String> getServerStatus(Integer serverId);

    /**
     * 重启服务器
     *
     * @param serverId 服务器ID
     * @return 重启结果
     */
    ReturnResult<Boolean> restartServer(Integer serverId);

    /**
     * 检查端口是否被占用
     *
     * @param port     端口号
     * @param serverId 排除的服务器ID（用于编辑时检查）
     * @return 检查结果
     */
    ReturnResult<Boolean> checkPortAvailable(Integer port, Integer serverId);

    /**
     * 克隆服务器配置
     *
     * @param sourceServerId 源服务器ID
     * @param newServerName  新服务器名称
     * @param newPort        新端口号
     * @return 克隆结果
     */
    ReturnResult<SystemServer> cloneServer(Integer sourceServerId, String newServerName, Integer newPort);

    /**
     * 获取运行中的服务器实例
     *
     * @param serverId 服务器ID
     * @return 服务器实例
     */
    ProtocolServer getRunningServerInstance(Integer serverId);

    /**
     * 应用配置更改到运行中的服务器
     *
     * @param serverId 服务器ID
     * @return 应用结果
     */
    ReturnResult<Boolean> applyConfigChanges(Integer serverId);

    /**
     * 删除配置到运行中的服务器
     *
     * @param serverId 运行中的服务器ID
     * @return 删除结果
     */
    ReturnResult<Boolean> applyDeleteConfigToRunningServer(Integer serverId);
}




