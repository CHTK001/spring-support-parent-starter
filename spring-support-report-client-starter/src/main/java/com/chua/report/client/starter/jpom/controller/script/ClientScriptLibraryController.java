/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.controller.script;

import cn.hutool.core.io.FileUtil;
import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import com.alibaba.fastjson2.JSONObject;
import com.chua.report.client.starter.jpom.common.BaseAgentController;
import com.chua.report.client.starter.jpom.common.i18n.I18nMessageUtil;
import com.chua.report.client.starter.jpom.common.validator.ValidatorItem;
import com.chua.report.client.starter.jpom.model.data.ScriptLibraryModel;
import com.chua.report.client.starter.jpom.service.script.ScriptLibraryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;

/**
 * @author bwcx_jzy1
 * @since 2024/6/1
 */
@RestController
@RequestMapping(value = "/script-library")
@Slf4j
public class ClientScriptLibraryController extends BaseAgentController {

    private final ScriptLibraryService scriptLibraryService;

    public ClientScriptLibraryController(ScriptLibraryService scriptLibraryService) {
        this.scriptLibraryService = scriptLibraryService;
    }

    @RequestMapping(value = "list", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<List<ScriptLibraryModel>> list() {
        List<ScriptLibraryModel> modelList = scriptLibraryService.list();
        return JsonMessage.success("", modelList);
    }

    @RequestMapping(value = "get", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<ScriptLibraryModel> get(@ValidatorItem String id) {
        ScriptLibraryModel scriptModel = scriptLibraryService.get(id);
        if (scriptModel != null) {
            return JsonMessage.success("", scriptModel);
        }
        return JsonMessage.fail(I18nMessageUtil.get("i18n.missing_script_message.af89"));
    }

    @RequestMapping(value = "save", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<String> save(@ValidatorItem String id,
                                     @ValidatorItem(msg = "i18n.script_content_cannot_be_empty.49be") String script,
                                     String description,
                                     String version) {
        File file = FileUtil.file(scriptLibraryService.getGlobalScriptDir(), id + ".json");
        ScriptLibraryModel scriptModel = new ScriptLibraryModel();
        scriptModel.setId(id);
        scriptModel.setScript(script);
        scriptModel.setDescription(description);
        scriptModel.setVersion(version);
        FileUtil.writeUtf8String(JSONObject.toJSONString(scriptModel), file);
        return JsonMessage.success(I18nMessageUtil.get("i18n.save_succeeded.3b10"));
    }

    @RequestMapping(value = "del", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<String> del(@ValidatorItem String id) {
        File file = FileUtil.file(scriptLibraryService.getGlobalScriptDir(), id + ".json");
        FileUtil.del(file);
        return JsonMessage.success(I18nMessageUtil.get("i18n.delete_success.0007"));
    }
}
