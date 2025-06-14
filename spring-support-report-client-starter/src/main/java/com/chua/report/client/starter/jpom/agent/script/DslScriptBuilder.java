/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.agent.script;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.LineHandler;
import cn.hutool.core.lang.Tuple;
import cn.hutool.core.util.StrUtil;
import com.chua.report.client.starter.jpom.common.common.i18n.I18nMessageUtil;
import com.chua.report.client.starter.jpom.common.model.EnvironmentMapBuilder;
import com.chua.report.client.starter.jpom.common.script.BaseRunScript;
import com.chua.report.client.starter.jpom.common.system.ExtConfigBean;
import com.chua.report.client.starter.jpom.common.util.CommandUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * dsl 执行脚本
 *
 * @author bwcx_jzy
 * @since 2022/1/15
 */
@Setter
@Slf4j
public class DslScriptBuilder extends BaseRunScript implements Runnable {


    private final String args;
    private String action;
    private File scriptFile;
    private boolean autoDelete;
    private EnvironmentMapBuilder environmentMapBuilder;

    public DslScriptBuilder(String action,
                            EnvironmentMapBuilder environmentMapBuilder,
                            String args,
                            String log,
                            Charset charset) {
        super(FileUtil.file(log), charset);
        this.action = action;
        this.environmentMapBuilder = environmentMapBuilder;
        this.args = args;
    }

    /**
     * 初始化
     */
    private ProcessBuilder init() {
        //
        String script = FileUtil.getAbsolutePath(scriptFile);
        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> command = StrUtil.splitTrim(args, StrUtil.SPACE);
        command.add(0, script);
        CommandUtil.paddingPrefix(command);
        log.debug(CollUtil.join(command, StrUtil.SPACE));
        processBuilder
                .environment()
                .putAll(environmentMapBuilder.environment());
        //
        String jpomExecPath = environmentMapBuilder.get("JPOM_EXEC_PATH");
        String projectPath = environmentMapBuilder.get("PROJECT_PATH");
        if (StrUtil.isNotEmpty(jpomExecPath) && StrUtil.isNotEmpty(projectPath)) {
            boolean absolutePath = FileUtil.isAbsolutePath(jpomExecPath);
            if (absolutePath) {
                processBuilder.directory(FileUtil.file(projectPath));
            } else {
                processBuilder.directory(FileUtil.file(projectPath, jpomExecPath));
            }
        } else {
            processBuilder.directory(FileUtil.getParent(scriptFile, 1));
        }
        processBuilder.redirectErrorStream(true);
        processBuilder.command(command);
        return processBuilder;
    }

    @Override
    public void run() {
        try {
            ProcessBuilder processBuilder = this.init();
            environmentMapBuilder.eachStr(this::info);
            //
            this.system(I18nMessageUtil.get("i18n.start_executing.f0b9"), this.action);
            process = processBuilder.start();
            inputStream = process.getInputStream();
            IoUtil.readLines(inputStream, ExtConfigBean.getConsoleLogCharset(), (LineHandler) line -> {
                String formatLine = formatLine(line);
                this.info(formatLine);
            });
            //
            int waitFor = process.waitFor();
            //
            this.system(I18nMessageUtil.get("i18n.execution_ended_with_detail.8f93"), this.action, waitFor);
        } catch (Exception e) {
            log.error(I18nMessageUtil.get("i18n.execution_exception.b0d5"), e);
            this.systemError(I18nMessageUtil.get("i18n.general_execution_exception.62e9") + e.getMessage());
        } finally {
            this.close();
        }
    }


    private String formatLine(String line) {
        return StrUtil.format("{} [{}] - {}", DateTime.now().toString(DatePattern.NORM_DATETIME_MS_FORMAT), this.action, line);
    }

    /**
     * 执行
     * <p>
     * 0 退出码
     * 1 日志
     */
    public Tuple syncExecute() {
        ProcessBuilder processBuilder = this.init();
        List<String> result = new ArrayList<>();
        int waitFor = -100;
        try {
            //
            process = processBuilder.start();
            inputStream = process.getInputStream();

            IoUtil.readLines(inputStream, ExtConfigBean.getConsoleLogCharset(), (LineHandler) line -> result.add(this.formatLine(line)));
            //
            waitFor = process.waitFor();
            // 插入第一行
            result.add(0, this.formatLine(StrUtil.format(I18nMessageUtil.get("i18n.exit_code.3b54"), waitFor)));
            //
        } catch (Exception e) {
            log.error(I18nMessageUtil.get("i18n.execution_exception.b0d5"), e);
            result.add(this.formatLine(StrUtil.format(I18nMessageUtil.get("i18n.general_execution_exception.62e9"), e.getMessage())));
        } finally {
            this.close();
        }
        return new Tuple(waitFor, result);
    }

    @Override
    protected void end(String msg) {

    }

    @Override
    protected void msgCallback(String msg) {

    }

    @Override
    public void close() {
        super.close();
        //
        if (autoDelete) {
            try {
                FileUtil.del(this.scriptFile);
            } catch (Exception ignored) {
            }
        }
    }


}
