package com.chua.starter.job.support.log;

import com.chua.common.support.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 任务日志文件处理器
 * <p>
 * 负责任务执行日志的文件存储操作，包括日志文件的创建、追加和读取。
 * 日志文件按日期和日志ID进行组织存储。
 * </p>
 *
 * <h3>目录结构</h3>
 * <pre>
 * {logBasePath}/
 *   ├── 2024-03-11/
 *   │   ├── 1001.log
 *   │   └── 1002.log
 *   ├── 2024-03-12/
 *   │   └── 1003.log
 *   └── gluesource/      (脚本源码目录)
 * </pre>
 *
 * <h3>主要方法</h3>
 * <ul>
 *     <li>{@link #initLogPath(String)} - 初始化日志路径</li>
 *     <li>{@link #makeLogFileName(Date, long)} - 生成日志文件名</li>
 *     <li>{@link #appendLog(String, String)} - 追加日志内容</li>
 *     <li>{@link #readLog(String, int)} - 读取日志内容</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 * @see DefaultJobLog
 * @see LogResult
 */
@Slf4j
public class JobFileAppender {

    /**
     * 日志基础路径
     */
    private static String logBasePath = "/data/applogs/job/jobhandler";
    private static String glueSrcPath = logBasePath.concat("/gluesource");

    /**
     * 初始化日志路径
     *
     * @param logPath 日志路径
     */
    public static void initLogPath(String logPath) {
        if (logPath != null && !logPath.trim().isEmpty()) {
            logBasePath = logPath;
        }
        // 创建基础目录
        File logPathDir = new File(logBasePath);
        if (!logPathDir.exists()) {
            logPathDir.mkdirs();
        }
        logBasePath = logPathDir.getPath();

        // 创建 glue 目录
        File glueBaseDir = new File(logPathDir, "gluesource");
        if (!glueBaseDir.exists()) {
            glueBaseDir.mkdirs();
        }
        glueSrcPath = glueBaseDir.getPath();
    }

    public static String getLogPath() {
        return logBasePath;
    }

    public static String getGlueSrcPath() {
        return glueSrcPath;
    }

    /**
     * 生成日志文件名
     *
     * @param triggerDate 触发日期
     * @param logId       日志ID
     * @return 日志文件名
     */
    public static String makeLogFileName(Date triggerDate, long logId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        File logFilePath = new File(getLogPath(), sdf.format(triggerDate));
        if (!logFilePath.exists()) {
            logFilePath.mkdir();
        }

        return logFilePath.getPath()
                .concat(File.separator)
                .concat(String.valueOf(logId))
                .concat(".log");
    }

    /**
     * 追加日志
     *
     * @param logFileName 日志文件名
     * @param appendLog   追加的日志内容
     */
    public static void appendLog(String logFileName, String appendLog) {
        if (logFileName == null || logFileName.trim().isEmpty()) {
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

        if (appendLog == null) {
            appendLog = "";
        }
        appendLog += "\r\n";

        try (FileOutputStream fos = new FileOutputStream(logFile, true)) {
            fos.write(appendLog.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 读取日志
     *
     * @param logFileName 日志文件名
     * @param fromLineNum 起始行号
     * @return 日志结果
     */
    public static LogResult readLog(String logFileName, int fromLineNum) {
        if (logFileName == null || logFileName.trim().isEmpty()) {
            return new LogResult(fromLineNum, 0, "读取日志失败，日志文件未找到", true);
        }
        File logFile = new File(logFileName);

        if (!logFile.exists()) {
            return new LogResult(fromLineNum, 0, "读取日志失败，日志文件不存在", true);
        }

        StringBuilder logContentBuffer = new StringBuilder();
        int toLineNum = 0;
        try (LineNumberReader reader = new LineNumberReader(
                new InputStreamReader(Files.newInputStream(logFile.toPath()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                toLineNum = reader.getLineNumber();
                if (toLineNum >= fromLineNum) {
                    logContentBuffer.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        return new LogResult(fromLineNum, toLineNum, logContentBuffer.toString(), false);
    }

    /**
     * 读取日志文件所有内容
     *
     * @param logFile 日志文件
     * @return 日志内容
     */
    public static String readLines(File logFile) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(logFile.toPath()), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
}
