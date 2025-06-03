/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.agent.socket;

import cn.hutool.core.util.StrUtil;
import cn.keepbx.jpom.model.JsonMessage;
import com.alibaba.fastjson2.JSONObject;
import com.chua.report.client.starter.jpom.agent.configuration.AgentConfig;
import com.chua.report.client.starter.jpom.agent.model.data.NodeScriptModel;
import com.chua.report.client.starter.jpom.agent.script.NodeScriptProcessBuilder;
import com.chua.report.client.starter.jpom.agent.service.script.ClientNodeScriptServer;
import com.chua.report.client.starter.jpom.agent.util.SocketSessionUtil;
import com.chua.report.client.starter.jpom.common.common.i18n.I18nMessageUtil;
import com.chua.report.client.starter.jpom.common.socket.ConsoleCommandOp;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 脚本模板socket
 *
 * @author bwcx_jzy
 * @since 2019/4/24
 */
@ServerEndpoint(value = "/script_run")
@Component
@Slf4j
public class AgentWebSocketScriptHandle extends BaseAgentWebSocketHandle {

    private static ClientNodeScriptServer clientNodeScriptServer;

    @Autowired
    public void init(ClientNodeScriptServer clientNodeScriptServer, AgentConfig agentConfig) {
        AgentWebSocketScriptHandle.clientNodeScriptServer = clientNodeScriptServer;
        setAgentAuthorize(agentConfig.getAuthorize());
    }

    @OnOpen
    public void onOpen(Session session) {
        try {
            setLanguage(session);
            if (super.checkAuthorize(session)) {
                return;
            }
            String id = this.getParameters(session, "id");
            String workspaceId = this.getParameters(session, "workspaceId");
            if (StrUtil.hasEmpty(id, workspaceId)) {
                SocketSessionUtil.send(session, I18nMessageUtil.get("i18n.unknown_script_template_or_workspace.27f1"));
                return;
            }

            NodeScriptModel nodeScriptModel = clientNodeScriptServer.getItem(id);
            if (nodeScriptModel == null) {
                SocketSessionUtil.send(session, I18nMessageUtil.get("i18n.no_script_template_found.0498"));
                return;
            }
            SocketSessionUtil.send(session, I18nMessageUtil.get("i18n.connection_successful_with_message.5cf2") + nodeScriptModel.getName());
        } catch (Exception e) {
            log.error(I18nMessageUtil.get("i18n.socket_error.18c1"), e);
            try {
                SocketSessionUtil.send(session, JsonMessage.getString(500, I18nMessageUtil.get("i18n.system_error.9417")));
                session.close();
            } catch (IOException e1) {
                log.error(e1.getMessage(), e1);
            }
        } finally {
            clearLanguage();
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) throws Exception {
        try {
            setLanguage(session);
            JSONObject json = JSONObject.parseObject(message);
            String scriptId = json.getString("scriptId");
            NodeScriptModel nodeScriptModel = clientNodeScriptServer.getItem(scriptId);
            if (nodeScriptModel == null) {
                SocketSessionUtil.send(session, I18nMessageUtil.get("i18n.no_script_template_specified.7d14") + scriptId);
                session.close();
                return;
            }
            String op = json.getString("op");
            ConsoleCommandOp consoleCommandOp = ConsoleCommandOp.valueOf(op);
            switch (consoleCommandOp) {
                case start: {
                    String args = json.getString("args");
                    String executeId = json.getString("executeId");
                    if (StrUtil.isEmpty(executeId)) {
                        SocketSessionUtil.send(session, I18nMessageUtil.get("i18n.no_execution_id.68dc"));
                        session.close();
                        return;
                    }
                    NodeScriptProcessBuilder.addWatcher(nodeScriptModel, executeId, args, session);
                    break;
                }
                case stop: {
                    String executeId = json.getString("executeId");
                    if (StrUtil.isEmpty(executeId)) {
                        SocketSessionUtil.send(session, I18nMessageUtil.get("i18n.no_execution_id.68dc"));
                        session.close();
                        return;
                    }
                    NodeScriptProcessBuilder.stopRun(executeId);
                    break;
                }
                case heart:
                default:
                    return;
            }
            // 记录操作人
            nodeScriptModel = clientNodeScriptServer.getItem(scriptId);
            String name = getOptUserName(session);
            nodeScriptModel.setLastRunUser(name);
            clientNodeScriptServer.updateItem(nodeScriptModel);
            json.put("code", 200);
            String value = I18nMessageUtil.get("i18n.execution_succeeded.f56c");
            json.put("msg", value);
            log.debug(json.toString());
            SocketSessionUtil.send(session, json.toString());
        } finally {
            clearLanguage();
        }
    }


    @Override
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        super.onClose(session, closeReason);
        NodeScriptProcessBuilder.stopWatcher(session);
    }

    @OnError
    @Override
    public void onError(Session session, Throwable thr) {
        super.onError(session, thr);
    }
}
