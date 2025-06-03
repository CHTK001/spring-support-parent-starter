/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.agent.controller.script;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chua.report.client.starter.jpom.agent.common.BaseAgentController;
import com.chua.report.client.starter.jpom.agent.model.data.NodeScriptExecLogModel;
import com.chua.report.client.starter.jpom.agent.model.data.NodeScriptModel;
import com.chua.report.client.starter.jpom.agent.script.NodeScriptProcessBuilder;
import com.chua.report.client.starter.jpom.agent.service.script.ClientNodeScriptServer;
import com.chua.report.client.starter.jpom.agent.service.script.NodeScriptExecLogServer;
import com.chua.report.client.starter.jpom.common.common.Const;
import com.chua.report.client.starter.jpom.common.common.i18n.I18nMessageUtil;
import com.chua.report.client.starter.jpom.common.common.validator.ValidatorItem;
import com.chua.report.client.starter.jpom.common.common.validator.ValidatorRule;
import com.chua.report.client.starter.jpom.common.util.CommandUtil;
import com.chua.report.client.starter.jpom.common.util.FileUtils;
import com.chua.report.client.starter.jpom.common.util.StringUtil;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 脚本管理
 *
 * @author bwcx_jzy
 * @since 2019/4/24
 */
@RestController
@RequestMapping(value = "/script")
public class ClientScriptController extends BaseAgentController {

    private final ClientNodeScriptServer clientNodeScriptServer;
    private final NodeScriptExecLogServer nodeScriptExecLogServer;

    public ClientScriptController(ClientNodeScriptServer clientNodeScriptServer,
                                  NodeScriptExecLogServer nodeScriptExecLogServer) {
        this.clientNodeScriptServer = clientNodeScriptServer;
        this.nodeScriptExecLogServer = nodeScriptExecLogServer;
    }

    @RequestMapping(value = "list.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<List<NodeScriptModel>> list() {
        return JsonMessage.success("", clientNodeScriptServer.list());
    }

    @RequestMapping(value = "item.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<NodeScriptModel> item(String id) {
        return JsonMessage.success("", clientNodeScriptServer.getItem(id));
    }

    @RequestMapping(value = "save.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<Object> save(NodeScriptModel nodeScriptModel, String type, String global, String nodeId) {
        Assert.notNull(nodeScriptModel, I18nMessageUtil.get("i18n.no_data.1ac0"));
        Assert.hasText(nodeScriptModel.getContext(), I18nMessageUtil.get("i18n.content_is_empty.3122"));
        //
        String autoExecCron = nodeScriptModel.getAutoExecCron();
        autoExecCron = StringUtil.checkCron(autoExecCron, s -> s);
        //
        boolean globalBool = Convert.toBool(global, false);
        if (globalBool) {
            nodeScriptModel.setWorkspaceId(Const.WORKSPACE_GLOBAL);
        } else {
            nodeScriptModel.setWorkspaceId(getWorkspaceId());
        }
        //
        nodeScriptModel.setContext(nodeScriptModel.getContext());
        NodeScriptModel eModel = clientNodeScriptServer.getItem(nodeScriptModel.getId());
        boolean needCreate = false;
        if ("add".equalsIgnoreCase(type)) {
            Assert.isNull(eModel, I18nMessageUtil.get("i18n.id_already_exists.6208"));

            nodeScriptModel.setId(IdUtil.fastSimpleUUID());
            nodeScriptModel.setNodeId(nodeId);
            clientNodeScriptServer.addItem(nodeScriptModel);
            return JsonMessage.success(I18nMessageUtil.get("i18n.addition_succeeded.3fda"));
        } else if ("sync".equalsIgnoreCase(type)) {
            // 同步脚本
            if (eModel == null) {
                eModel = new NodeScriptModel();
                eModel.setId(nodeScriptModel.getId());
                eModel.setNodeId(nodeId);
                needCreate = true;
            } else {
                if (!eModel.global() && nodeScriptModel.global()) {
                    // 修改绑定的节点id
                    eModel.setNodeId(nodeId);
                }
            }
            eModel.setScriptType("server-sync");
            eModel.setWorkspaceId(nodeScriptModel.getWorkspaceId());
        }
        Assert.notNull(eModel, I18nMessageUtil.get("i18n.data_not_exist.41f9"));
        eModel.setName(nodeScriptModel.getName());
        eModel.setAutoExecCron(autoExecCron);
        eModel.setDescription(nodeScriptModel.getDescription());
        eModel.setContext(nodeScriptModel.getContext());
        eModel.setDefArgs(nodeScriptModel.getDefArgs());
        if (needCreate) {
            clientNodeScriptServer.addItem(eModel);
        } else {
            clientNodeScriptServer.updateItem(eModel);
        }
        return JsonMessage.success(I18nMessageUtil.get("i18n.modify_success.69be"));
    }

    @RequestMapping(value = "del.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<Object> del(String id) {
        clientNodeScriptServer.deleteItem(id);
        return JsonMessage.success(I18nMessageUtil.get("i18n.delete_success.0007"));
    }

    /**
     * 获取的日志
     *
     * @param id        id
     * @param executeId 执行ID
     * @param line      需要获取的行号
     * @return json
     */
    @RequestMapping(value = "log", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<JSONObject> getNowLog(@ValidatorItem(msg = "i18n.log_id_missing.3500") String id,
                                              @ValidatorItem(msg = "i18n.missing_execution_id.14d0") String executeId,
                                              @ValidatorItem(value = ValidatorRule.POSITIVE_INTEGER, msg = "i18n.incorrect_line_number.5877") int line) {
        NodeScriptModel item = clientNodeScriptServer.getItem(id);
        Assert.notNull(item, I18nMessageUtil.get("i18n.no_data_found.4ffb"));
        File logFile = item.logFile(executeId);
        Assert.state(FileUtil.isFile(logFile), I18nMessageUtil.get("i18n.log_file_error.473b"));

        JSONObject data = FileUtils.readLogFile(logFile, line);
        // 运行中
        data.put("run", NodeScriptProcessBuilder.isRun(executeId));
        return JsonMessage.success("", data);
    }

    /**
     * 删除日志
     *
     * @param id        id
     * @param executeId 执行ID
     * @return json
     */
    @RequestMapping(value = "del_log", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<Object> delLog(@ValidatorItem(msg = "i18n.log_id_missing.3500") String id,
                                       @ValidatorItem(msg = "i18n.missing_execution_id.14d0") String executeId) {
        NodeScriptModel item = clientNodeScriptServer.getItem(id);
        if (item == null) {
            return JsonMessage.success(I18nMessageUtil.get("i18n.script_template_not_exist.1d5b"));
        }
        Assert.notNull(item, I18nMessageUtil.get("i18n.no_data_found.4ffb"));
        File logFile = item.logFile(executeId);
        boolean fastDel = CommandUtil.systemFastDel(logFile);
        Assert.state(!fastDel, I18nMessageUtil.get("i18n.delete_log_file_failure.bf0b"));
        return JsonMessage.success(I18nMessageUtil.get("i18n.delete_success.0007"));
    }

    /**
     * 执行
     *
     * @param id     ID
     * @param args   执行参数
     * @param params 环境变量参数
     * @return json
     */
    @RequestMapping(value = "exec", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<String> exec(@ValidatorItem() String id, String args, String params) {
        NodeScriptModel item = clientNodeScriptServer.getItem(id);
        Assert.notNull(item, I18nMessageUtil.get("i18n.script_not_exist.b180"));
        String nowUserName = getNowUserName();

        Map<String, String> paramMap = Opt.ofBlankAble(params)
                .map(JSONObject::parseObject)
                .map(jsonObject -> {
                    Map<String, String> paramMap1 = new HashMap<>(10);
                    for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                        String key = StrUtil.format("trigger_{}", entry.getKey());
                        key = StrUtil.toUnderlineCase(key);
                        paramMap1.put(key, StrUtil.toString(entry.getValue()));
                    }
                    return paramMap1;
                })
                .orElse(null);
        //

        String execute = clientNodeScriptServer.execute(item, 2, nowUserName, null, args, paramMap);
        return JsonMessage.success(I18nMessageUtil.get("i18n.start_execution.00d7"), execute);
    }

    /**
     * 同步定时执行日志
     *
     * @param pullCount 领取个数
     * @return json
     */
    @RequestMapping(value = "pull_exec_log", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<List<NodeScriptExecLogModel>> pullExecLog(@ValidatorItem int pullCount) {
        Assert.state(pullCount > 0, "pull count error");
        List<NodeScriptExecLogModel> list = nodeScriptExecLogServer.list();
        list = CollUtil.sub(list, 0, pullCount);
        if (list == null) {
            return JsonMessage.success("", Collections.emptyList());
        }
        return JsonMessage.success("", list);
    }

    /**
     * 删除定时执行日志
     *
     * @param jsonObject 拉起参数
     * @return json
     */
    @RequestMapping(value = "del_exec_log", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<Object> delExecLog(@RequestBody JSONObject jsonObject) {
        JSONArray ids = jsonObject.getJSONArray("ids");
        if (ids != null) {
            for (Object id : ids) {
                String idStr = (String) id;
                nodeScriptExecLogServer.deleteItem(idStr);
            }
        }
        return JsonMessage.success(I18nMessageUtil.get("i18n.delete_success.0007"));
    }

    @RequestMapping(value = "change-workspace-id", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<Object> changeWorkspaceId(@ValidatorItem() String id, String newWorkspaceId, String newNodeId) {
        Assert.hasText(newWorkspaceId, I18nMessageUtil.get("i18n.select_workspace_to_modify.ac87"));
        Assert.hasText(newWorkspaceId, I18nMessageUtil.get("i18n.select_node_to_modify.6617"));
        NodeScriptModel item = clientNodeScriptServer.getItem(id);
        Assert.notNull(item, I18nMessageUtil.get("i18n.script_info_not_found.bd8d"));
        //
        NodeScriptModel update = new NodeScriptModel();
        update.setNodeId(newNodeId);
        update.setWorkspaceId(newWorkspaceId);
        clientNodeScriptServer.updateById(update, item.getId());
        return JsonMessage.success(I18nMessageUtil.get("i18n.modify_success.69be"));
    }
}
