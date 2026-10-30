package com.chua.starter.rpc.support.attrbute;

import lombok.Data;

/**
 * rpc属性
 *
 * @since 2024/04/07
 * @author CH
 */
@Data
public class RpcAttribute {

    /**
     * RPC服务标识
     * <p>示例: "userService"</p>
     */
    public static final String ID = "id";

    /**
     * 接口定义
     * <p>示例: "com.example.UserService"</p>
     */
    public static final String INTERFACE = "interface";

    /**
     * 接口名称
     * <p>示例: "UserService"</p>
     */
    public static final String INTERFACE_NAME = "interfaceName";

    /**
     * 接口类
     * <p>示例: "com.example.UserService.class"</p>
     */
    public static final String INTERFACE_CLASS = "interfaceClass";

    /**
     * 降级的 RPC 提供者名称
     * <p>例如 "springbean"：RPC 远端不可用时回退到本地 Spring Bean。</p>
     */
    public static final String DEGRADE = "degrade";

    /**
     * 未找到 RPC Bean 时是否抛出异常
     */
    public static final String EXCEPTION = "exception";

}
