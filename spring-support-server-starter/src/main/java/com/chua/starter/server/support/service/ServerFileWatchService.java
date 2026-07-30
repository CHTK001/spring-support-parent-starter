package com.chua.starter.server.support.service;

import com.chua.starter.server.support.model.ServerFileWatchTicket;

public interface ServerFileWatchService {

    ServerFileWatchTicket createWatch(Integer serverId, String path) throws Exception;

    boolean stopWatch(Integer serverId, Long watchId);
}
