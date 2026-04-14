package com.chua.starter.server.support.service;

import com.chua.starter.server.support.model.ServerExposureSummary;
import com.chua.starter.server.support.model.ServerExposurePortView;
import com.chua.starter.server.support.model.ServerExposurePortMeta;
import java.util.List;

public interface ServerExposureService {

    ServerExposureSummary getSummary(Integer serverId);

    ServerExposureSummary refresh(Integer serverId);

    List<ServerExposurePortView> listPorts(Integer serverId, boolean refresh);

    List<ServerExposurePortView> listPorts(
            Integer serverId,
            boolean refresh,
            String keyword,
            String protocol,
            String state,
            Integer limit
    );

    ServerExposurePortMeta portMeta(Integer serverId, boolean refresh);
}
