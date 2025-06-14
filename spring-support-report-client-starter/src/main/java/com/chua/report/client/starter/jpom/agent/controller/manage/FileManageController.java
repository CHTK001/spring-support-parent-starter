/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.agent.controller.manage;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import com.chua.report.client.starter.jpom.agent.common.BaseAgentController;
import com.chua.report.client.starter.jpom.agent.common.commander.CommandOpResult;
import com.chua.report.client.starter.jpom.agent.configuration.AgentConfig;
import com.chua.report.client.starter.jpom.common.common.i18n.I18nMessageUtil;
import com.chua.report.client.starter.jpom.common.common.validator.ValidatorItem;
import com.chua.report.client.starter.jpom.common.util.CompressionFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * @author bwcx_jzy
 * @since 2023/3/28
 */
@RestController
@RequestMapping(value = "/manage/file2/")
@Slf4j
public class FileManageController extends BaseAgentController {

    private final AgentConfig agentConfig;

    public FileManageController(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
    }

    @RequestMapping(value = "upload-sharding", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<CommandOpResult> uploadSharding(MultipartFile file,
                                                        String sliceId,
                                                        Integer totalSlice,
                                                        Integer nowSlice,
                                                        String fileSumMd5) throws Exception {
        String tempPathName = agentConfig.getFixedTempPathName();
        this.uploadSharding(file, tempPathName, sliceId, totalSlice, nowSlice, fileSumMd5);
        return JsonMessage.success(I18nMessageUtil.get("i18n.upload_success.a769"));
    }

    @RequestMapping(value = "sharding-merge", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public IJsonMessage<CommandOpResult> shardingMerge(String type,
                                                       @ValidatorItem(msg = "i18n.file_directory_missing.6b1d") String path,
                                                       Integer stripComponents,
                                                       String sliceId,
                                                       Integer totalSlice,
                                                       String fileSumMd5) throws Exception {
        String tempPathName = agentConfig.getFixedTempPathName();
        File successFile = this.shardingTryMerge(tempPathName, sliceId, totalSlice, fileSumMd5);
        File lib = FileUtil.file(path);
        // 处理上传文件
        if ("unzip".equals(type)) {
            // 解压
            try {
                int stripComponentsValue = Convert.toInt(stripComponents, 0);
                CompressionFileUtil.unCompress(successFile, lib, stripComponentsValue);
            } finally {
                if (!FileUtil.del(successFile)) {
                    log.error(I18nMessageUtil.get("i18n.delete_file_failure_with_full_stop.6c96") + successFile.getPath());
                }
            }
        } else {
            // 移动文件到对应目录
            FileUtil.mkdir(lib);
            FileUtil.move(successFile, lib, true);
        }
        return JsonMessage.success(I18nMessageUtil.get("i18n.upload_success.a769"));
    }
}
