package com.chua.report.client.starter.job.handler;

import com.chua.report.client.starter.job.GlueTypeEnum;
import com.chua.report.client.starter.job.ScriptUtil;
import com.chua.report.client.starter.job.log.DefaultJobLog;
import com.chua.report.client.starter.job.log.JobFileAppender;
import com.chua.report.client.starter.job.log.JobLog;

import java.io.File;

public class ScriptJobHandler implements JobHandler {

    private final int jobId;
    private final long glueUpdatetime;
    private final String gluesource;
    private final GlueTypeEnum glueType;
    public ScriptJobHandler(int jobId, long glueUpdatetime, String gluesource, GlueTypeEnum glueType) {
        this.jobId = jobId;
        this.glueUpdatetime = glueUpdatetime;
        this.gluesource = gluesource;
        this.glueType = glueType;

        // clean old script file
        File glueSrcPath = new File(JobFileAppender.getGlueSrcPath());
        if (glueSrcPath.exists()) {
            File[] glueSrcFileList = glueSrcPath.listFiles();
            if (glueSrcFileList != null) {
                for (File glueSrcFileItem : glueSrcFileList) {
                    if (glueSrcFileItem.getName().startsWith(jobId + "_")) {
                        glueSrcFileItem.delete();
                    }
                }
            }
        }

    }

    public long getGlueUpdatetime() {
        return glueUpdatetime;
    }

    @Override
    public void execute() throws Exception {

        if (!glueType.isScript()) {
            DefaultJobLog.log("glueType[" + glueType + "] invalid.");
            return;
        }

        // cmd
        String cmd = glueType.getCmd();

        // make script file
        String scriptFileName = JobFileAppender.getGlueSrcPath()
                .concat(File.separator)
                .concat(String.valueOf(jobId))
                .concat("_")
                .concat(String.valueOf(glueUpdatetime))
                .concat(glueType.getSuffix());
        File scriptFile = new File(scriptFileName);
        if (!scriptFile.exists()) {
            ScriptUtil.markScriptFile(scriptFileName, gluesource);
        }

        // log file
        String logFileName = jobId + ".log";

        // script params：0=param、1=分片序号、2=分片总数
        String[] scriptParams = new String[3];

        // invoke
        DefaultJobLog.log("----------- script file:" + scriptFileName + " -----------");
        int exitValue = ScriptUtil.execToFile(cmd, scriptFileName, logFileName, scriptParams);

        if (exitValue == 0) {
        } else {
            DefaultJobLog.log("script exit value(" + exitValue + ") is failed");
        }

    }

}
