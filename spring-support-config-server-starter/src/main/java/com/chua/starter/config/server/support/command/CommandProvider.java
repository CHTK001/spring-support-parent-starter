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
     * 处理数据
     *
     * @param subscribe       订阅的数据类型
     * @param applicationName 当前应用名称
     * @param data            数据
     * @param dataType
     * @param dataManager
     * @param request         请求
     * @return 结果
     */
    ReturnResult<String> command(String subscribe, String applicationName, String data, String dataType, DataManager dataManager, HttpServletRequest request);
}
