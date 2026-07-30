package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerDetectedService;
import com.chua.starter.server.support.service.impl.ServerHostCommandExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * 服务发现 SPI。
 *
 * <p>按协议/平台封装服务探测命令与结果解析，业务层仅负责编排与入库。
 *
 * @author CH
 */
public interface ServerServiceDiscoverySpi {

    /**
     * 执行服务探测并返回标准化结果。
     *
     * @param host 服务器主机
     * @param commandExecutor 服务器命令执行器
     * @param objectMapper JSON 解析器
     * @return 探测结果列表
     * @throws Exception 探测失败时抛出异常
     */
    List<ServerDetectedService> detect(
            ServerHost host,
            ServerHostCommandExecutor commandExecutor,
            ObjectMapper objectMapper
    ) throws Exception;
}

