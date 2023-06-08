package com.chua.starter.oauth.server.support.resolver;

/**
 * 日志服务
 *
 * @author CH
 */
public interface LoggerResolver {
    /**
     * 注册日志
     *
     * @param module  模块
     * @param code    状态码
     * @param message 消息
     * @param address 登录信息
     */
    void register(String module, String code, String message, String address);
}
