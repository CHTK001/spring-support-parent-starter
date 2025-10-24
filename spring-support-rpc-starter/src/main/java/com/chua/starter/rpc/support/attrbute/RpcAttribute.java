package com.chua.starter.rpc.support.attrbute;

import lombok.Data;

/**
 * rpc属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/07
 */
@Data
public class RpcAttribute {

    /**
     * RPC服务ID
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

}
