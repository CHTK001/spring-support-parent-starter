package com.chua.starter.config.server.support.command;

import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.config.server.support.manager.DataManager;

import javax.servlet.http.HttpServletRequest;

/**
 * 命令处理
 *
 * @author CH
 * @since 2022/8/1 9:20
 */
public interface CommandProvider {
    /**
     * 命令
     * 处理数据
     *
     * @param applicationName    当前应用名称
     * @param data               数据
     * @param dataType           数据类型
     * @param dataManager        数据管理器
     * @param request            请求
     * @param applicationProfile 应用程序配置文件
     * @return 结果
     */
    ReturnResult<String> command(String applicationName, String data, String dataType, String applicationProfile, DataManager dataManager, HttpServletRequest request);
}
