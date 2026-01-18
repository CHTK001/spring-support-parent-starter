package com.chua.report.client.starter.job.handler;

import com.chua.report.client.starter.job.GlueTypeEnum;
import com.chua.report.client.starter.job.ScriptUtil;
import com.chua.report.client.starter.job.log.DefaultJobLog;
import com.chua.report.client.starter.job.log.JobFileAppender;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

/**
 * 脚本方式的作业处理程序
 * <p>
 * 支持执行Shell、Python、PHP、NodeJS、PowerShell等脚本文件。
 * 脚本内容存储在数据库中，执行时会生成临时脚本文件并调用系统命令执行。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Slf4j
public class ScriptJobHandler implements JobHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ScriptJobHandler.class);

    /**
     * 任务ID
     */
    private final int jobId;

    /**
     * GLUE更新时间戳
     */
    @Getter
    private final long glueUpdatetime;

    /**
     * 获取 GLUE 更新时间戳
     *
     * @return GLUE 更新时间戳
     */
    public long getGlueUpdatetime() {
        return glueUpdatetime;
    }

    /**
     * 脚本源码
     */
    private final String gluesource;

    /**
     * 脚本类型
     */
    private final GlueTypeEnum glueType;

    /**
     * 构造函数
     *
     * @param jobId          任务ID
     * @param glueUpdatetime GLUE更新时间戳
     * @param gluesource     脚本源码
     * @param glueType       脚本类型
     */
    public ScriptJobHandler(int jobId, long glueUpdatetime, String gluesource, GlueTypeEnum glueType) {
        this.jobId = jobId;
        this.glueUpdatetime = glueUpdatetime;
        this.gluesource = gluesource;
        this.glueType = glueType;

        // 清理旧版本的脚本文件
        cleanOldScriptFiles();
    }

    /**
     * 清理旧版本的脚本文件
     */
    private void cleanOldScriptFiles() {
        File glueSrcPath = new File(JobFileAppender.getGlueSrcPath());
        if (glueSrcPath.exists()) {
            File[] glueSrcFileList = glueSrcPath.listFiles();
            if (glueSrcFileList != null) {
                for (File glueSrcFileItem : glueSrcFileList) {
                    // 删除该任务的所有旧脚本文件
                    if (glueSrcFileItem.getName().startsWith(jobId + "_")) {
                        boolean deleted = glueSrcFileItem.delete();
                        if (deleted) {
                            log.debug("已删除旧版本脚本文件: {}", glueSrcFileItem.getName());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void execute() throws Exception {
        // 验证脚本类型
        if (!glueType.isScript()) {
            String errorMsg = "脚本类型无效: " + glueType;
            DefaultJobLog.log(errorMsg);
            log.error(errorMsg);
            return;
        }

        // 获取执行命令
        String cmd = glueType.getCmd();
        log.debug("脚本执行命令: {}", cmd);

        // 构建脚本文件路径
        String scriptFileName = JobFileAppender.getGlueSrcPath()
                .concat(File.separator)
                .concat(String.valueOf(jobId))
                .concat("_")
                .concat(String.valueOf(glueUpdatetime))
                .concat(glueType.getSuffix());

        // 创建脚本文件（如果不存在）
        File scriptFile = new File(scriptFileName);
        if (!scriptFile.exists()) {
            ScriptUtil.markScriptFile(scriptFileName, gluesource);
            log.debug("创建脚本文件: {}", scriptFileName);
        }

        // 日志文件路径
        String logFileName = jobId + ".log";

        // 脚本参数：0=param、1=分片序号、2=分片总数
        String[] scriptParams = new String[3];

        // 执行脚本
        DefaultJobLog.log("----------- 脚本文件:" + scriptFileName + " -----------");
        log.info("开始执行脚本任务, 任务ID: {}, 脚本类型: {}", jobId, glueType.getDesc());

        int exitValue = ScriptUtil.execToFile(cmd, scriptFileName, logFileName, scriptParams);

        // 检查执行结果
        if (exitValue != 0) {
            String errorMsg = "脚本执行失败, 退出码: " + exitValue;
            DefaultJobLog.log(errorMsg);
            log.error("脚本执行失败, 任务ID: {}, 退出码: {}", jobId, exitValue);
        } else {
            log.info("脚本执行成功, 任务ID: {}", jobId);
        }
    }
}
