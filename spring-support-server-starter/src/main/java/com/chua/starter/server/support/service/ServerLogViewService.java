package com.chua.starter.server.support.service;

import com.chua.starter.server.support.model.SystemLogEntry;
import com.chua.starter.server.support.model.SystemLogSource;
import java.util.List;

public interface ServerLogViewService {

    List<SystemLogSource> listSources(Integer serverId) throws Exception;

    List<SystemLogEntry> listLogs(Integer serverId, String source, String level,
                                   String keyword, Integer limit) throws Exception;
}
