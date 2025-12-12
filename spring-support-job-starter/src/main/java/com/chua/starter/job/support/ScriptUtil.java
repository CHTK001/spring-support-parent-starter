package com.chua.starter.job.support;

import com.chua.starter.job.support.log.DefaultJobLog;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 脚本工具类
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
public class ScriptUtil {

    /**
     * 创建脚本文件
     *
     * @param scriptFileName 脚本文件名
     * @param content        脚本内容
     * @throws IOException IO异常
     */
    public static void markScriptFile(String scriptFileName, String content) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(scriptFileName)) {
            fileOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 执行脚本并输出到文件
     *
     * @param command    命令
     * @param scriptFile 脚本文件
     * @param logFile    日志文件
     * @param params     参数
     * @return 退出值
     * @throws IOException IO异常
     */
    public static int execToFile(String command, String scriptFile, String logFile, String... params) throws IOException {
        FileOutputStream fileOutputStream = null;
        Thread inputThread = null;
        Thread errThread = null;
        try {
            fileOutputStream = new FileOutputStream(logFile, true);

            List<String> cmdarray = new ArrayList<>();
            cmdarray.add(command);
            cmdarray.add(scriptFile);
            if (params != null) {
                Collections.addAll(cmdarray, params);
            }
            String[] cmdarrayFinal = cmdarray.toArray(new String[0]);

            final Process process = Runtime.getRuntime().exec(cmdarrayFinal);

            final FileOutputStream finalFileOutputStream = fileOutputStream;
            inputThread = new Thread(() -> {
                try {
                    copy(process.getInputStream(), finalFileOutputStream, new byte[1024]);
                } catch (IOException e) {
                    DefaultJobLog.log(e.getMessage());
                }
            });
            errThread = new Thread(() -> {
                try {
                    copy(process.getErrorStream(), finalFileOutputStream, new byte[1024]);
                } catch (IOException e) {
                    DefaultJobLog.log(e.getMessage());
                }
            });
            inputThread.start();
            errThread.start();

            int exitValue = process.waitFor();

            inputThread.join();
            errThread.join();

            return exitValue;
        } catch (Exception e) {
            DefaultJobLog.log(e.getMessage());
            return -1;
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    DefaultJobLog.log(e.getMessage());
                }
            }
            if (inputThread != null && inputThread.isAlive()) {
                inputThread.interrupt();
            }
            if (errThread != null && errThread.isAlive()) {
                errThread.interrupt();
            }
        }
    }

    private static long copy(InputStream inputStream, OutputStream outputStream, byte[] buffer) throws IOException {
        try {
            long total = 0;
            for (; ; ) {
                int res = inputStream.read(buffer);
                if (res == -1) {
                    break;
                }
                if (res > 0) {
                    total += res;
                    if (outputStream != null) {
                        outputStream.write(buffer, 0, res);
                    }
                }
            }
            outputStream.flush();
            inputStream.close();
            return total;
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }
}
