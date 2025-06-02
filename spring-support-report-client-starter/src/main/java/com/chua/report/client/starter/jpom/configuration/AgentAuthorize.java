/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.configuration;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson2.JSONObject;
import com.chua.report.client.starter.jpom.common.Const;
import com.chua.report.client.starter.jpom.common.i18n.I18nMessageUtil;
import com.chua.report.client.starter.jpom.model.system.AgentAutoUser;
import com.chua.report.client.starter.jpom.system.ExtConfigBean;
import com.chua.report.client.starter.jpom.system.JpomRuntimeException;
import com.chua.report.client.starter.jpom.util.JsonFileUtil;
import com.chua.report.client.starter.jpom.util.JvmUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;

/**
 * agent 端授权账号信息
 *
 * @author bwcx_jzy
 * @since 2019/4/17
 */
@Slf4j
@Data
@ConfigurationProperties("plugin.maintenance.authorize")
public class AgentAuthorize {
    /**
     * 账号
     */
    private String agentName;
    /**
     * 密码
     */
    private String agentPwd;
    /**
     * 授权加密字符串
     */
    private String authorize;

    public String getAuthorize() {
        return null;
    }

    public void setAuthorize(String authorize) {
        // 不能外部 set
    }

    /**
     * 判断授权是否正确
     *
     * @param authorize 授权
     * @return true 正确
     */
    public boolean checkAuthorize(String authorize) {
        return StrUtil.equals(authorize, this.authorize);
    }

    /**
     * 获取项目运行数据存储文件夹路径
     *
     * @return 文件夹路径
     */
    public String getDataPath() {
        String dataPath = FileUtil.normalize(ExtConfigBean.getPath() + StrUtil.SLASH + Const.DATA);
        FileUtil.mkdir(dataPath);
        return dataPath;
    }

    /**
     * 检查是否配置密码
     */
    private void checkPwd() {
        File path = FileUtil.file(getDataPath(), Const.AUTHORIZE);
        if (StrUtil.isNotEmpty(agentPwd)) {
            // 有指定密码 清除旧密码信息
            FileUtil.del(path);
            log.info("Authorization information has been customized,account：{}", this.agentName);
            return;
        }
        if (FileUtil.exist(path)) {
            // 读取旧密码
            String json = FileUtil.readString(path, CharsetUtil.CHARSET_UTF_8);
            AgentAutoUser autoUser = JSONObject.parseObject(json, AgentAutoUser.class);
            if (!StrUtil.equals(autoUser.getAgentName(), this.agentName)) {
                throw new JpomRuntimeException("The existing login name is inconsistent with the configured login name");
            }
            String oldAgentPwd = autoUser.getAgentPwd();
            if (StrUtil.isNotEmpty(oldAgentPwd)) {
                this.agentPwd = oldAgentPwd;
                log.info("Already authorized account:{} password:{} Authorization information storage location：{}", this.agentName, this.agentPwd, FileUtil.getAbsolutePath(path));
                return;
            }
        }
        this.agentPwd = RandomUtil.randomString(10);
        AgentAutoUser autoUser = new AgentAutoUser();
        autoUser.setAgentName(this.agentName);
        autoUser.setAgentPwd(this.agentPwd);
        // 写入文件中
        JsonFileUtil.saveJson(path, autoUser.toJson());
        log.info("Automatically generate authorized account:{}  password:{}  Authorization information storage location：{}", this.agentName, this.agentPwd, FileUtil.getAbsolutePath(path));
    }

    public void init() {
        if (StrUtil.isEmpty(this.agentName)) {
            throw new JpomRuntimeException("The agent login name cannot be empty");
        }
        if (StrUtil.isEmpty(this.authorize)) {
            this.checkPwd();
            // 生成密码授权字符串
            this.authorize = SecureUtil.sha1(this.agentName + "@" + this.agentPwd);
        } else {
            log.warn(I18nMessageUtil.get("i18n.authorized_cannot_be_reloaded.6ece"));
        }
        //
        JvmUtil.checkJpsNormal();
    }
}
