package com.chua.report.client.starter.job.log;

import com.chua.common.support.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 调度任务日志文件追加器，负责将执行日志写入本地文件系统。
 *
 * <p>按日期目录组织日志文件，并支持 GLUE 脚本源码的读写。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class JobFileAppender {



    /**
       * 日志 基础 路径
     *
     * strut like:
     * 	---/
     * 	---/gluesource/
     * 	---/gluesource/10_1514171108000.js
     * 	---/gluesource/10_1514171108000.js
     * 	---/2017-12-25/
      * ---/2017-12-25/639.日志
      * ---/2017-12-25/821.日志
     *
     */
    private static String logBasePath = "/data/applogs/job/jobhandler";
    /** 胶水src路径 */
    private static String glueSrcPath = logBasePath.concat("/gluesource");
    /**
     * 初始化记录日志路径
     *
     * @param logPath 日志路径
     */
    public static void initLogPath(String logPath){
 // 初始化
        if (logPath!=null && logPath.trim().length()>0) {
            logBasePath = logPath;
        }
 // mk 基础 dir
        File logPathDir = new File(logBasePath);
        if (!logPathDir.exists()) {
            logPathDir.mkdirs();
        }
        logBasePath = logPathDir.getPath();

 // mk 胶水 dir
        File glueBaseDir = new File(logPathDir, "gluesource");
        if (!glueBaseDir.exists()) {
            glueBaseDir.mkdirs();
        }
        glueSrcPath = glueBaseDir.getPath();
    }
    /**
     * 获取记录日志路径
     *
     * @return 获取日志路径的结果
     */
    public static String getLogPath() {
        return logBasePath;
    }
    /**
     * 获取胶水src路径
     *
     * @return 获取胶水src路径的结果
     */
    public static String getGlueSrcPath() {
        return glueSrcPath;
    }

    /**
      * 日志 文件名, like "日志路径/yyyy-MM-dd/9999.日志"
     *
     * @param triggerDate
     * @param logId
     * @return
     */
    public static String makeLogFileName(Date triggerDate, long logId) {

 // 文件路径/yyyy-MM-dd
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd"); // avoid 并发 problem, 能否 not be 静态
        File logFilePath = new File(getLogPath(), sdf.format(triggerDate));
        if (!logFilePath.exists()) {
            logFilePath.mkdir();
        }

 // 文件路径/yyyy-MM-dd/9999.日志
        String logFileName = logFilePath.getPath()
                .concat(File.separator)
                .concat(String.valueOf(logId))
                .concat(".log");
        return logFileName;
    }

    /**
      * 追加 日志
     *
     * @param logFileName
     * @param appendLog
     */
    public static void appendLog(String logFileName, String appendLog) {

 // 日志 文件
        if (logFileName==null || logFileName.trim().length()==0) {
            return;
        }
        File logFile = new File(logFileName);

        FileUtils.mkParentDirs(logFile);
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                return;
            }
        }

 // 日志
        if (appendLog == null) {
            appendLog = "";
        }
        appendLog += "\r\n";

 // 追加 文件 内容
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(logFile, true);
            fos.write(appendLog.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }

    }

    /**
      * 支持 读取 日志-文件
     *
     * @param logFileName
     * @return log 内容
     * @param fromLineNum 从线num
     */
    public static LogResult readLog(String logFileName, int fromLineNum){
 // valid 日志 文件
        if (logFileName==null || logFileName.trim().isEmpty()) {
            /**
             * 日志结果。
             * @param fromLineNum 从线num
             * @param 0 0
             * @param fail 失败
             * @param found" found"
             * @param true true
             * @return 日志结果的结果
             */
            return new LogResult(fromLineNum, 0, "readLog fail, logFile not found", true);
        }
        File logFile = new File(logFileName);

        if (!logFile.exists()) {
            /**
             * 日志结果。
             * @param fromLineNum 从线num
             * @param 0 0
             * @param fail 失败
             * @param exists" exists"
             * @param true true
             * @return 日志结果的结果
             */
            return new LogResult(fromLineNum, 0, "readLog fail, logFile not exists", true);
        }

 // 读取 文件
        StringBuilder logContentBuffer = new StringBuilder();
        int toLineNum = 0;
        LineNumberReader reader = null;
        try {
 // 读取 = 新 线数字读取(新 文件读取(日志文件));
            reader = new LineNumberReader(new InputStreamReader(Files.newInputStream(logFile.toPath()), StandardCharsets.UTF_8));
            String line = null;

            while ((line = reader.readLine())!=null) {
                toLineNum = reader.getLineNumber(); // [从, 转为], 启动 as 1
                if (toLineNum >= fromLineNum) {
                    logContentBuffer.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }

 // 结果
        /**
         * 日志结果。
         * @param fromLineNum 从线num
         * @param toLineNum 转为线num
         * @param logContentBuffer.toString( 日志内容缓冲.tostring(
         * @return 日志结果的结果
         */
        return new LogResult(fromLineNum, toLineNum, logContentBuffer.toString(), false);

    }

    /**
      * 读取 日志 数据
     * @param logFile
     * @return log 线 内容
     */
    public static String readLines(File logFile){
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(Files.newInputStream(logFile.toPath()), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        return null;
    }
}

