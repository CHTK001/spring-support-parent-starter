package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.ssh.support.ssh.SshClient;
import com.chua.starter.monitor.server.entity.MonitorTerminal;
import com.chua.starter.monitor.server.entity.MonitorTerminalBase;

import java.util.List;

/**
 * 终端
 * @author CH
 * @since 2024/6/19
 */
public interface MonitorTerminalService extends IService<MonitorTerminal> {

    /**
     * 启动终端代理。
     * <p>此方法用于启动传入的终端代理实例。如果启动成功，返回true；否则返回false。</p>
     *
     * @param monitorTerminal 终端代理实例，不能为空。
     * @return boolean 返回启动结果，成功为true，失败为false。
     */
    ReturnResult<Boolean> start(MonitorTerminal monitorTerminal);

    /**
     * 停止终端代理。
     * <p>此方法用于停止传入的终端代理实例。如果停止成功，返回true；否则返回false。</p>
     *
     * @param monitorTerminal 终端代理实例，不能为空。
     * @return boolean 返回停止结果，成功为true，失败为false。
     */
    ReturnResult<Boolean> stop(MonitorTerminal monitorTerminal);

    /**
     * 根据请求ID获取会话对象。
     *
     * 此方法旨在通过请求ID检索与特定请求相关的会话对象。会话对象可能包含关于用户会话的重要信息，
     * 如用户身份验证状态、会话变量等。
     *
     * @param requestId 请求ID，用于唯一标识一个请求。这是检索会话的关键依据。
     * @return 与给定请求ID关联的会话对象。如果找不到匹配的会话，可能返回null。
     */
    SshClient getClient(String requestId);

    /**
     * 判断是否需要显示监控终端的指示器。
     *
     * 该方法用于根据某些条件判断是否应该在监控终端上显示指示器。指示器可能是用于提示用户
     * 注意某些重要信息或者警告的图形元素。这个方法通过传入的监控终端代理对象来做出决策。
     *
     * @param monitorTerminal 监控终端的代理对象，用于访问和操作监控终端的特性。
     * @return 如果需要显示指示器，则返回true；否则返回false。
     */
    boolean indicator(MonitorTerminal monitorTerminal);


    /**
     * 通过调用ifconfig命令获取网络配置信息。
     *
     * @param monitorTerminal 一个MonitorTerminal实例，用于执行命令和获取结果。
     * @return 返回执行ifconfig命令后得到的网络配置信息字符串。
     */
    String ifconfig(MonitorTerminal monitorTerminal);

    /**
     * 获取监控终端的基础信息列表。
     *
     * @param monitorTerminal 一个MonitorTerminal实例，用于执行命令和获取结果。
     * @return 返回一个包含监控终端基础信息的List集合。
     */
    List<MonitorTerminalBase> base(MonitorTerminal monitorTerminal);

    /**
     * 对监控终端进行基础升级操作。
     *
     * 本方法旨在通过传入的监控终端对象，执行一系列基础升级步骤，并返回升级后的终端列表。
     * 升级过程中，可能会涉及到终端硬件或软件的更新，以提升终端的功能或性能。
     *
     * @param monitorTerminal 待升级的监控终端对象，包含了终端的详细信息。
     * @return 返回升级后的监控终端列表。如果升级过程中产生了多个升级后的终端实例，
     *         则列表中将包含所有升级后的终端。如果升级不涉及终端数量的改变，则返回列表中只有一个终端。
     */
    List<MonitorTerminalBase> baseUpgrade(MonitorTerminal monitorTerminal);
}
