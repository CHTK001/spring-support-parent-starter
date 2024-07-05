package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.indicator.WIndicator;
import com.chua.starter.monitor.server.entity.MonitorTerminal;
import com.chua.starter.monitor.server.entity.MonitorTerminalBase;
import com.chua.starter.monitor.server.pojo.Last;

import java.util.List;

/**
 * MonitorTerminalBaseService接口定义了对监控终端基础信息的服务操作。
 * 它继承自IService<MonitorTerminalBase>，表示该服务适用于监控终端基础信息的增删改查等操作。
 * @author CH
 * @since 2024/6/20
 */
public interface MonitorTerminalBaseService extends IService<MonitorTerminalBase> {

    /**
     * 方法w用于对监控终端代理对象进行操作，返回一个W类型的列表。
     * 该方法的具体功能和参数意义需要根据实现情况进行详细注释。
     *
     * @param monitorProxy 监控终端的代理对象，代表一个具体的监控终端实例。
     * @return 返回一个W类型的列表，列表内容与监控终端代理对象的操作结果相关。
     */
    ReturnResult<List<WIndicator>> w(MonitorTerminal monitorProxy);


    /**
     * 方法last用于获取监控终端的“最近”信息，返回一个Last类型的列表。
     * 该方法的具体功能和参数意义需要根据实现情况进行详细注释。
     *
     * @param monitorProxy 监控终端的代理对象，代表一个具体的监控终端实例。
     * @return 返回一个Last类型的列表，列表内容与监控终端代理对象的操作结果相关。
     */
    ReturnResult<List<Last>> last(MonitorTerminal monitorProxy);
}
