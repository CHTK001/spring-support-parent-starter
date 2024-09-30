package com.chua.report.server.starter.service;

import com.chua.ssh.support.ssh.SshClient;

/**
 * 终端
 * @author CH
 * @since 2024/6/19
 */
public interface TerminalService {

    /**
     * 根据请求ID获取会话对象。
     *
     * 此方法旨在通过请求ID检索与特定请求相关的会话对象。会话对象可能包含关于用户会话的重要信息，
     * 如用户身份验证状态、会话变量等。
     *
     * @param genId 请求ID，用于唯一标识一个请求。这是检索会话的关键依据。
     * @return 与给定请求ID关联的会话对象。如果找不到匹配的会话，可能返回null。
     */
    SshClient getClient(String genId);

}
