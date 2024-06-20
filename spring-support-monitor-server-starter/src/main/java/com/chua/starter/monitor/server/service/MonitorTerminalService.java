package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.Session;
import com.chua.starter.monitor.server.entity.MonitorTerminal;

/**
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
    Session getSession(String requestId);

    /**
     * 判断是否需要显示监控终端的指示器。
     *
     * 该方法用于根据某些条件判断是否应该在监控终端上显示指示器。指示器可能是用于提示用户
     * 注意某些重要信息或者警告的图形元素。这个方法通过传入的监控终端代理对象来做出决策。
     *
     * @param monitorProxy 监控终端的代理对象，用于访问和操作监控终端的特性。
     * @return 如果需要显示指示器，则返回true；否则返回false。
     */
    boolean indicator(MonitorTerminal monitorProxy);
}
