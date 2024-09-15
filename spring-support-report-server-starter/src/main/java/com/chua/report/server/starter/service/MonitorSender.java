package com.chua.report.server.starter.service;

import com.chua.common.support.discovery.Discovery;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.report.client.starter.endpoint.ModuleType;
import com.chua.starter.common.support.project.Project;
/**
 * MonitorSender接口定义了监控数据发送的相关方法
 * 它提供了异步上传、同步上传监控数据以及获取项目信息的功能
 * 主要用于集成监控数据发送功能到系统中
 *
 * @author CH
 * @since 2024/9/12
 */
public interface MonitorSender {
    /**
     * 异步上传监控数据
     *
     * 此方法用于异步地将监控数据上传到指定的项目中
     * 它允许在不等待操作完成的情况下立即返回，适合对性能要求较高的场景
     *
     * @param o 要上传的监控数据对象
     * @param discovery 发现服务，用于定位数据上传的目标项目
     * @param params 附加参数，可以用于传递额外的信息或配置
     * @param type 模块类型，指定数据所属的模块或系统
     */
    void upload(Object o, Discovery discovery, String params, ModuleType type);

    /**
     * 同步上传监控数据并返回结果
     *
     * 此方法用于同步地将监控数据上传到指定的项目中，并等待上传操作完成
     * 它返回一个包含上传结果的ReturnResult对象，适合需要确认数据上传结果的场景
     *
     * @param o 要上传的监控数据对象
     * @param discovery 发现服务，用于定位数据上传的目标项目
     * @param params 附加参数，可以用于传递额外的信息或配置
     * @param type 模块类型，指定数据所属的模块或系统
     * @return ReturnResult<String> 包含上传结果的对象，成功时包含上传的数据ID，失败时包含错误信息
     */
    ReturnResult<String> uploadSync(Object o, Discovery discovery, String params, ModuleType type);

    /**
     * 获取项目信息
     *
     * 此方法用于从发现服务中获取当前项目的详细信息
     * 它主要用于确定数据上传的目标项目，或者获取项目的配置信息
     *
     * @param discovery 发现服务，用于定位当前项目
     * @return Project 当前项目的详细信息对象，包含项目的基本信息和配置
     */
    Project getProject(Discovery discovery);
}
