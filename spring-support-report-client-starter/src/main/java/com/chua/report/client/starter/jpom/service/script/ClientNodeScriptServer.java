/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.service.script;

import cn.hutool.core.date.SystemClock;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.cron.task.Task;
import cn.hutool.extra.spring.SpringUtil;
import cn.keepbx.jpom.cron.ICron;
import com.chua.report.client.starter.jpom.ClientJpomApplication;
import com.chua.report.client.starter.jpom.common.AgentConst;
import com.chua.report.client.starter.jpom.common.Const;
import com.chua.report.client.starter.jpom.cron.CronUtils;
import com.chua.report.client.starter.jpom.model.data.NodeScriptExecLogModel;
import com.chua.report.client.starter.jpom.model.data.NodeScriptModel;
import com.chua.report.client.starter.jpom.script.NodeScriptProcessBuilder;
import com.chua.report.client.starter.jpom.service.BaseWorkspaceOptService;
import com.chua.report.client.starter.jpom.system.ExtConfigBean;
import com.chua.report.client.starter.jpom.util.CommandUtil;
import com.chua.report.client.starter.jpom.util.FileUtils;
import com.chua.report.client.starter.jpom.util.StringUtil;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 脚本模板管理
 *
 * @author bwcx_jzy
 * @since 2019/4/24
 */
@Service
public class ClientNodeScriptServer extends BaseWorkspaceOptService<NodeScriptModel> implements ICron<NodeScriptModel> {
    private final NodeScriptExecLogServer execLogServer;
    private final ClientJpomApplication clientJpomApplication;
    private final ScriptLibraryService scriptLibraryService;

    public ClientNodeScriptServer(NodeScriptExecLogServer execLogServer,
                                  ClientJpomApplication clientJpomApplication,
                                  ScriptLibraryService scriptLibraryService) {
        super(AgentConst.SCRIPT);
        this.execLogServer = execLogServer;
        this.clientJpomApplication = clientJpomApplication;
        this.scriptLibraryService = scriptLibraryService;
    }

    @Override
    public void addItem(NodeScriptModel nodeScriptModel) {
        super.addItem(nodeScriptModel);
        this.checkCron(nodeScriptModel);
    }

    @Override
    public void updateItem(NodeScriptModel nodeScriptModel) {
        super.updateItem(nodeScriptModel);
        this.checkCron(nodeScriptModel);
    }

    /**
     * @param id 数据id
     * @see NodeScriptModel#logFile(String)
     */
    @Override
    public void deleteItem(String id) {
        NodeScriptModel nodeScriptModel = getItem(id);
        if (nodeScriptModel != null) {
            File file = nodeScriptModel.scriptPath();
            FileUtil.del(file);
        }
        super.deleteItem(id);
        String taskId = "script:" + id;
        CronUtils.remove(taskId);
    }

    public File toExecuteFile(NodeScriptModel nodeScriptModel) {
        String dataPath = clientJpomApplication.getDataPath();
        File scriptFile = FileUtil.file(dataPath, Const.SCRIPT_RUN_CACHE_DIRECTORY, StrUtil.format("{}.{}", IdUtil.fastSimpleUUID(), CommandUtil.SUFFIX));
        String context = nodeScriptModel.getContext();
        //
        context = scriptLibraryService.referenceReplace(context);
        FileUtils.writeScript(context, scriptFile, ExtConfigBean.getConsoleLogCharset());
        return scriptFile;
    }

    @Override
    public boolean checkCron(NodeScriptModel nodeScriptModel) {
        String id = "script:" + nodeScriptModel.getId();
        String autoExecCron = nodeScriptModel.getAutoExecCron();
        autoExecCron = StringUtil.parseCron(autoExecCron);
        if (StrUtil.isEmpty(autoExecCron)) {
            CronUtils.remove(id);
            return false;
        } else {
            CronUtils.upsert(id, autoExecCron, new CronTask(nodeScriptModel.getId()));
            return true;
        }
    }

    @Override
    public List<NodeScriptModel> queryStartingList() {
        return this.list();
    }

    /**
     * 执行脚本
     *
     * @param scriptServerItem 脚本
     * @param type             类型
     * @param args             参数
     * @return 执行记录ID
     */
    public String execute(NodeScriptModel scriptServerItem, int type, String uerName, String workspaceId, String args, Map<String, String> paramMap) {
        NodeScriptExecLogModel nodeScriptExecLogModel = new NodeScriptExecLogModel();
        nodeScriptExecLogModel.setId(IdUtil.fastSimpleUUID());
        nodeScriptExecLogModel.setCreateTimeMillis(SystemClock.now());
        nodeScriptExecLogModel.setScriptId(scriptServerItem.getId());
        nodeScriptExecLogModel.setScriptName(scriptServerItem.getName());
        nodeScriptExecLogModel.setModifyUser(uerName);
        nodeScriptExecLogModel.setWorkspaceId(StrUtil.emptyToDefault(workspaceId, scriptServerItem.getWorkspaceId()));
        nodeScriptExecLogModel.setTriggerExecType(type);
        execLogServer.addItem(nodeScriptExecLogModel);
        String userArgs = StrUtil.emptyToDefault(args, scriptServerItem.getDefArgs());
        // 执行
        NodeScriptProcessBuilder.create(scriptServerItem, nodeScriptExecLogModel.getId(), userArgs, paramMap);
        return nodeScriptExecLogModel.getId();
    }

    private static class CronTask implements Task {
        private final String id;

        public CronTask(String id) {
            this.id = id;
        }

        @Override
        public void execute() {
            ClientNodeScriptServer clientNodeScriptServer = SpringUtil.getBean(ClientNodeScriptServer.class);
            NodeScriptModel scriptServerItem = clientNodeScriptServer.getItem(id);
            if (scriptServerItem == null) {
                return;
            }
            // 创建记录
            NodeScriptExecLogServer execLogServer = SpringUtil.getBean(NodeScriptExecLogServer.class);
            NodeScriptExecLogModel nodeScriptExecLogModel = new NodeScriptExecLogModel();
            nodeScriptExecLogModel.setId(IdUtil.fastSimpleUUID());
            nodeScriptExecLogModel.setCreateTimeMillis(SystemClock.now());
            nodeScriptExecLogModel.setScriptId(scriptServerItem.getId());
            nodeScriptExecLogModel.setScriptName(scriptServerItem.getName());
            nodeScriptExecLogModel.setWorkspaceId(scriptServerItem.getWorkspaceId());
            nodeScriptExecLogModel.setTriggerExecType(1);
            execLogServer.addItem(nodeScriptExecLogModel);
            // 执行
            NodeScriptProcessBuilder.create(scriptServerItem, nodeScriptExecLogModel.getId(), scriptServerItem.getDefArgs(), null);
        }
    }
}
