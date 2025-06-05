package com.chua.starter.common.support.cmd;

import org.apache.commons.exec.*;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 命令执行工具类
 *
 * @author CH
 * @since 2025/6/5 11:12
 */

public class CommandExecutor {
    /**
     * 执行命令并等待指定时间
     *
     * @param command 要执行的命令
     * @param millis  等待时间(毫秒)
     * @return 命令执行结果
     */
    public static CommandResult execute(String command, long millis) throws Exception {
        return executeWithRealtimeOutput(command, millis, it -> {
        }, it -> {
        });
    }

    /**
     * 执行命令并实时监听输出
     *
     * @param command        要执行的命令
     * @param timeout        超时时间(毫秒)
     * @param outputConsumer 标准输出消费者
     * @param errorConsumer  错误输出消费者
     * @return 命令执行结果
     */
    public static CommandResult executeWithRealtimeOutput(
            String command,
            long timeout,
            Consumer<String> outputConsumer,
            Consumer<String> errorConsumer) throws Exception {

        CommandLine cmdLine = isWindows() ?
                CommandLine.parse("cmd.exe /c " + command) :
                CommandLine.parse("/bin/bash -c " + command);

        // 创建实时输出流
        RealtimeLogOutputStream stdoutStream = new RealtimeLogOutputStream(true, outputConsumer);
        RealtimeLogOutputStream stderrStream = new RealtimeLogOutputStream(false, errorConsumer);

        PumpStreamHandler streamHandler = new PumpStreamHandler(stdoutStream, stderrStream);

        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(streamHandler);
        executor.setExitValue(0); // 设置成功退出码

        ExecuteWatchdog watchdog = new ExecuteWatchdog(timeout);
        executor.setWatchdog(watchdog);

        // 使用Future来监控执行状态
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Integer> future = executorService.submit(() -> {
            try {
                return executor.execute(cmdLine);
            } finally {
                executorService.shutdown();
            }
        });

        try {
            int exitValue = future.get(timeout, TimeUnit.MILLISECONDS);
            return new CommandResult(
                    exitValue,
                    String.join("\n", stdoutStream.getLogQueue()),
                    String.join("\n", stderrStream.getLogQueue())
            );
        } catch (TimeoutException e) {
            watchdog.destroyProcess(); // 确保进程被终止
            throw new CommandTimeoutException("Command timed out after " + timeout + "ms");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ExecuteException ee) {
                return new CommandResult(
                        ee.getExitValue(),
                        String.join("\n", stdoutStream.getLogQueue()),
                        String.join("\n", stderrStream.getLogQueue())
                );
            }
            throw new IOException("Command execution failed", e.getCause());
        }
    }

    /**
     * 检查当前操作系统是否为Windows
     */
    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }


    /**
     * 命令执行结果封装类
     */
    public static class CommandResult {
        private final int exitCode;
        private final String output;
        private final String error;

        public CommandResult(int exitCode, String output, String error) {
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }

        // Getters
        public int getExitCode() {
            return exitCode;
        }

        public String getOutput() {
            return output;
        }

        public String getError() {
            return error;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    /**
     * 命令超时异常
     */
    public static class CommandTimeoutException extends Exception {
        public CommandTimeoutException(String message) {
            super(message);
        }
    }
}