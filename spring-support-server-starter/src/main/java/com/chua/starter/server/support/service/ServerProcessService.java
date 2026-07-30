package com.chua.starter.server.support.service;

import com.chua.starter.server.support.model.ServerProcessAiAdvice;
import com.chua.starter.server.support.model.ServerProcessCommandResult;
import com.chua.starter.server.support.model.ServerProcessRelationGraph;
import com.chua.starter.server.support.model.ServerProcessView;
import java.util.List;
import java.util.Map;

public interface ServerProcessService {

    List<ServerProcessView> listProcesses(Integer serverId, String keyword, Integer limit, String stateFilter) throws Exception;

    ServerProcessView getProcess(Integer serverId, Long pid) throws Exception;

    ServerProcessCommandResult terminateProcess(Integer serverId, Long pid, boolean force) throws Exception;

    ServerProcessAiAdvice analyzeProcess(Integer serverId, Long pid) throws Exception;

    /**
     * 获取进程统计摘要（总数、僵尸数、运行数等）。
     */
    Map<String, Integer> getProcessStats(Integer serverId) throws Exception;

    /**
     * 清理僵尸进程：向父进程发送 SIGCHLD 信号促使其回收子进程。
     */
    ServerProcessCommandResult zombieCleanup(Integer serverId, Long zombiePid) throws Exception;

    /**
     * 获取进程关系图谱（父进程、子进程、关联客户端 IP）。
     */
    ServerProcessRelationGraph getProcessRelations(Integer serverId, Long pid) throws Exception;
}
