package com.chua.starter.server.support.service;

import com.chua.starter.server.support.model.ServerProcessAiAdvice;
import com.chua.starter.server.support.model.ServerProcessCommandResult;
import com.chua.starter.server.support.model.ServerProcessView;
import java.util.List;

public interface ServerProcessService {

    List<ServerProcessView> listProcesses(Integer serverId, String keyword, Integer limit) throws Exception;

    ServerProcessView getProcess(Integer serverId, Long pid) throws Exception;

    ServerProcessCommandResult terminateProcess(Integer serverId, Long pid, boolean force) throws Exception;

    ServerProcessAiAdvice analyzeProcess(Integer serverId, Long pid) throws Exception;
}
