package com.chua.report.client.starter.job;

import com.chua.report.client.starter.job.log.DefaultJobLog;
import com.chua.report.client.starter.job.log.JobLog;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
   * 1、内嵌编译器如"pythoninterpreter"无法引用扩展包，因此推荐使用Java调用控制台进程方式"Runtime.获取runtime().执行()"来运行脚本(Shell或Python)；
   * 2、因为通过Java调用控制台进程方式实现，需要保证目标机器路径路径正确配置对应编译器；
 *  3、暂时脚本执行日志只能在脚本执行结束后一次性获取，无法保证实时性；因此为确保日志实时性，可改为将脚本打印的日志存储在指定的日志文件上；
   * 4、Python 异常输出优先级高于标准输出，体现在日志文件中，因此推荐通过日志方式打日志保持和异常信息一致；否则用prinf日志顺序会错乱
 *
 * Created by xuxueli on 17/2/25.
 * @author CH
 * @since 4.0.0
 */
public class ScriptUtil {

    /**
      * make script 文件
     *
     * @param scriptFileName
     * @param content
     * @throws IOException
     */
    public static void markScriptFile(String scriptFileName, String content) throws IOException {
 // make 文件,   文件路径/gluesource/666-123456789.py
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(scriptFileName);
            fileOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
            fileOutputStream.close();
        } catch (Exception e) {
            throw e;
        }finally{
            if(fileOutputStream != null){
                fileOutputStream.close();
            }
        }
    }

    /**
     * 脚本执行，日志文件实时输出
     *
     * @param command
     * @param scriptFile
     * @param logFile
     * @param params
     * @return
     * @throws IOException
     */
    public static int execToFile(String command, String scriptFile, String logFile, String... params) throws IOException {

        FileOutputStream fileOutputStream = null;
        Thread inputThread = null;
        Thread errThread = null;
        try {
 // 文件
            fileOutputStream = new FileOutputStream(logFile, true);

 // 命令
            List<String> cmdarray = new ArrayList<>();
            cmdarray.add(command);
            cmdarray.add(scriptFile);
            if (params != null) {
                Collections.addAll(cmdarray, params);
            }
            String[] cmdarrayFinal = cmdarray.toArray(new String[cmdarray.size()]);

 // 处理-执行
            final Process process = Runtime.getRuntime().exec(cmdarrayFinal);

 // 日志-thread
            final FileOutputStream finalFileOutputStream = fileOutputStream;
            inputThread = new Thread(() -> {
                try {
                    /**
                     * 副本。
                     * @param process.getInputStream( 处理.获取输入流(
                     */
                    copy(process.getInputStream(), finalFileOutputStream, new byte[1024]);
                } catch (IOException e) {
                    DefaultJobLog.log(e.getMessage());
                }
            });
            errThread = new Thread(() -> {
                try {
                    /**
                     * 副本。
                     * @param process.getErrorStream( 处理.获取错误流(
                     */
                    copy(process.getErrorStream(), finalFileOutputStream, new byte[1024]);
                } catch (IOException e) {
                    DefaultJobLog.log(e.getMessage());
                }
            });
            inputThread.start();
            errThread.start();

 // 处理-wait
            int exitValue = process.waitFor(); // exit 编码: 0=成功, 1=错误

 // 日志-thread 连接
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

    /**
      * 数据流副本（输入自动关闭，输出不处理）
     *
     * @param inputStream
     * @param outputStream
     * @param buffer
     * @return
     * @throws IOException
     */
    private static long copy(InputStream inputStream, OutputStream outputStream, byte[] buffer) throws IOException {
        try {
            long total = 0;
            for (;;) {
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
 // 出 = 空;
            inputStream.close();
            inputStream = null;
            return total;
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }


}

