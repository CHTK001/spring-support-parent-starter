/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.agent.model.data;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.chua.report.client.starter.jpom.agent.ClientJpomApplication;
import com.chua.report.client.starter.jpom.common.common.i18n.I18nMessageUtil;
import com.chua.report.client.starter.jpom.common.script.CommandParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;

/**
 * 脚本模板
 *
 * @author bwcx_jzy
 * @since 2019/4/24
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NodeScriptModel extends BaseWorkspaceModel {
    /**
     * 最后执行人员
     */
    private String lastRunUser;
    /**
     * 脚本内容
     */
    private String context;
    /**
     * 自动执行的 cron
     */
    private String autoExecCron;
    /**
     * 默认参数
     */
    private String defArgs;
    /**
     * 描述
     */
    private String description;
    /**
     * 脚本类型:server-sync
     */
    private String scriptType;

    public static File scriptPath(String id) {
        if (StrUtil.isEmpty(id)) {
            throw new IllegalArgumentException(I18nMessageUtil.get("i18n.id_is_empty.3bbf"));
        }
        File path = ClientJpomApplication.getInstance().getScriptPath();
        return FileUtil.file(path, id);
    }

    public String getLastRunUser() {
        return StrUtil.emptyToDefault(lastRunUser, StrUtil.DASHED);
    }

    public void setDefArgs(String defArgs) {
        this.defArgs = CommandParam.convertToParam(defArgs);
    }

    public File scriptPath() {
        return scriptPath(getId());
    }

    public File logFile(String executeId) {
        if (StrUtil.isEmpty(getId())) {
            throw new IllegalArgumentException(I18nMessageUtil.get("i18n.id_is_empty.3bbf"));
        }
        File path = ClientJpomApplication.getInstance().getScriptPath();
        return FileUtil.file(path, getId(), "log", executeId + ".log");
    }
}
