package com.chua.starter.config.server.cammand;

import com.chua.common.support.function.NameAware;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.config.server.properties.ConfigServerProperties;

import javax.servlet.http.HttpServletRequest;

/**
 * 命令处理
 *
 * @author CH
 * @since 2022/8/1 9:20
 */
public interface CommandProvider extends NameAware {
    /**
     * 处理数据
     *
     * @param binder                 绑定的名称
     * @param data                   数据
     * @param configServerProperties 配置
     * @param request                请求
     * @return 结果
     */
    ReturnResult<String> command(String binder, String data, ConfigServerProperties configServerProperties, HttpServletRequest request);
}
