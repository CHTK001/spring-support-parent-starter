package com.chua.starter.server.support.service;

import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerGuacamoleConfig;

public interface ServerGuacamoleService {

    ServerGuacamoleConfig buildConfig(ServerHost host);
}
