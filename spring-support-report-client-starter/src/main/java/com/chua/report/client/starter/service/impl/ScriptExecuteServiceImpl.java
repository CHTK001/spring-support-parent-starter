package com.chua.report.client.starter.service.impl;

import com.chua.report.client.starter.pojo.ScriptExecuteRequest;
import com.chua.report.client.starter.pojo.ScriptExecuteResponse;
import com.chua.report.client.starter.service.ScriptExecuteService;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * 脚本执行服务实现
 * 
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
public class ScriptExecuteServiceImpl implements ScriptExecuteService {

    private static final Map<Long, Process> RUNNING_PROCESSES = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    
    private static final List<String> SUPPORTED_SCRIPT_TYPES = Arrays.asList(
        "shell", "batch", "powershell", "python", "javascript", "groovy", "lua", "perl", "ruby", "php"
    );

    @Override
    public ScriptExecuteResponse executeScript(ScriptExecuteRequest request) {
        try {
            request.validate();
        } catch (IllegalArgumentException e) {
            return ScriptExecuteResponse.exception(request.getScriptType(), e.getMessage(), e);
        }

        if (request.isAsync()) {
            return executeScriptAsync(request);
        } else {
            return executeScriptSync(request);
        }
    }

    @Override
    public ScriptExecuteResponse executeShellScript(String scriptContent) {
        ScriptExecuteRequest request = ScriptExecuteRequest.createShellScript(scriptContent);
        return executeScript(request);
    }

    @Override
    public ScriptExecuteResponse executePowerShellScript(String scriptContent) {
        ScriptExecuteRequest request = ScriptExecuteRequest.createPowerShellScript(scriptContent);
        return executeScript(request);
    }

    @Override
    public ScriptExecuteResponse executeBatchScript(String scriptContent) {
        ScriptExecuteRequest request = ScriptExecuteRequest.createBatchScript(scriptContent);
        return executeScript(request);
    }

    @Override
    public ScriptExecuteResponse executePythonScript(String scriptContent) {
        ScriptExecuteRequest request = ScriptExecuteRequest.createPythonScript(scriptContent);
        return executeScript(request);
    }

    @Override
    public ScriptExecuteResponse executeJavaScriptScript(String scriptContent) {
        ScriptExecuteRequest request = ScriptExecuteRequest.builder()
                .scriptType("javascript")
                .scriptContent(scriptContent)
                .build();
        return executeScript(request);
    }

    @Override
    public ScriptExecuteResponse executeScriptAsync(ScriptExecuteRequest request) {
        try {
            LocalDateTime startTime = LocalDateTime.now();
            
            // 创建脚本文件
            Path scriptFile = createScriptFile(request);
            
            // 构建命令
            List<String> command = buildCommand(request, scriptFile);
            
            // 创建进程构建器
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(request.getWorkingDirectoryOrDefault()));
            
            // 设置环境变量
            if (request.getEnvironment() != null) {
                processBuilder.environment().putAll(request.getEnvironment());
            }
            
            // 启动进程
            Process process = processBuilder.start();
            long processId = process.pid();
            
            // 保存进程引用
            RUNNING_PROCESSES.put(processId, process);
            
            return ScriptExecuteResponse.builder()
                    .success(true)
                    .scriptType(request.getScriptType())
                    .status(ScriptExecuteResponse.ExecutionStatus.RUNNING.getCode())
                    .message("脚本已开始异步执行")
                    .processId(processId)
                    .startTime(startTime)
                    .scriptFilePath(scriptFile.toString())
                    .workingDirectory(request.getWorkingDirectoryOrDefault())
                    .build();
                    
        } catch (Exception e) {
            log.error("异步执行脚本失败", e);
            return ScriptExecuteResponse.exception(request.getScriptType(), "异步执行脚本失败", e);
        }
    }

    @Override
    public boolean stopScript(Long processId) {
        Process process = RUNNING_PROCESSES.get(processId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            RUNNING_PROCESSES.remove(processId);
            return true;
        }
        return false;
    }

    @Override
    public ScriptExecuteResponse getScriptStatus(Long processId) {
        Process process = RUNNING_PROCESSES.get(processId);
        if (process == null) {
            return ScriptExecuteResponse.builder()
                    .success(false)
                    .status(ScriptExecuteResponse.ExecutionStatus.FAILED.getCode())
                    .message("进程不存在")
                    .processId(processId)
                    .build();
        }

        if (process.isAlive()) {
            return ScriptExecuteResponse.builder()
                    .success(true)
                    .status(ScriptExecuteResponse.ExecutionStatus.RUNNING.getCode())
                    .message("脚本正在执行")
                    .processId(processId)
                    .build();
        } else {
            // 进程已结束，获取结果
            RUNNING_PROCESSES.remove(processId);
            
            try {
                int exitCode = process.exitValue();
                String stdout = readStream(process.getInputStream());
                String stderr = readStream(process.getErrorStream());
                
                boolean success = exitCode == 0;
                String status = success ? 
                    ScriptExecuteResponse.ExecutionStatus.SUCCESS.getCode() : 
                    ScriptExecuteResponse.ExecutionStatus.FAILED.getCode();
                
                return ScriptExecuteResponse.builder()
                        .success(success)
                        .status(status)
                        .exitCode(exitCode)
                        .stdout(stdout)
                        .stderr(stderr)
                        .processId(processId)
                        .endTime(LocalDateTime.now())
                        .build();
            } catch (Exception e) {
                return ScriptExecuteResponse.exception("unknown", "获取脚本状态失败", e);
            }
        }
    }

    @Override
    public boolean isScriptTypeSupported(String scriptType) {
        return SUPPORTED_SCRIPT_TYPES.contains(scriptType.toLowerCase());
    }

    @Override
    public List<String> getSupportedScriptTypes() {
        return new ArrayList<>(SUPPORTED_SCRIPT_TYPES);
    }

    /**
     * 同步执行脚本
     */
    private ScriptExecuteResponse executeScriptSync(ScriptExecuteRequest request) {
        LocalDateTime startTime = LocalDateTime.now();
        Path scriptFile = null;
        
        try {
            // 创建脚本文件
            scriptFile = createScriptFile(request);
            
            // 构建命令
            List<String> command = buildCommand(request, scriptFile);
            
            // 创建进程构建器
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(request.getWorkingDirectoryOrDefault()));
            
            // 设置环境变量
            if (request.getEnvironment() != null) {
                processBuilder.environment().putAll(request.getEnvironment());
            }
            
            // 启动进程
            Process process = processBuilder.start();
            long processId = process.pid();
            
            // 等待执行完成或超时
            boolean finished = process.waitFor(request.getTimeoutOrDefault(), TimeUnit.SECONDS);
            LocalDateTime endTime = LocalDateTime.now();
            
            if (!finished) {
                // 超时，强制终止进程
                process.destroyForcibly();
                return ScriptExecuteResponse.timeout(request.getScriptType(), "脚本执行超时")
                        .withTimeRange(startTime, endTime)
                        .withProcessInfo(processId, request.getWorkingDirectoryOrDefault(), request.getExecuteUser())
                        .withScriptFilePath(scriptFile.toString());
            }
            
            // 获取执行结果
            int exitCode = process.exitValue();
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            
            boolean success = exitCode == 0;
            ScriptExecuteResponse response;
            
            if (success) {
                response = ScriptExecuteResponse.success(request.getScriptType(), stdout, exitCode);
            } else {
                response = ScriptExecuteResponse.failure(request.getScriptType(), "脚本执行失败", stderr, exitCode);
            }
            
            return response
                    .withTimeRange(startTime, endTime)
                    .withProcessInfo(processId, request.getWorkingDirectoryOrDefault(), request.getExecuteUser())
                    .withScriptFilePath(scriptFile.toString());
                    
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ScriptExecuteResponse.interrupted(request.getScriptType(), "脚本执行被中断")
                    .withTimeRange(startTime, LocalDateTime.now())
                    .withScriptFilePath(scriptFile != null ? scriptFile.toString() : null);
        } catch (Exception e) {
            log.error("执行脚本失败", e);
            return ScriptExecuteResponse.exception(request.getScriptType(), "执行脚本失败", e)
                    .withTimeRange(startTime, LocalDateTime.now())
                    .withScriptFilePath(scriptFile != null ? scriptFile.toString() : null);
        } finally {
            // 清理临时脚本文件
            if (scriptFile != null) {
                try {
                    Files.deleteIfExists(scriptFile);
                } catch (Exception e) {
                    log.warn("删除临时脚本文件失败: {}", scriptFile, e);
                }
            }
        }
    }

    /**
     * 创建脚本文件
     */
    private Path createScriptFile(ScriptExecuteRequest request) throws IOException {
        ScriptExecuteRequest.ScriptType scriptType = request.getScriptTypeEnum();
        if (scriptType == null) {
            throw new IllegalArgumentException("不支持的脚本类型: " + request.getScriptType());
        }
        
        // 创建临时目录
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "scripts");
        Files.createDirectories(tempDir);
        
        // 创建脚本文件
        String fileName = "script_" + System.currentTimeMillis() + scriptType.getExtension();
        Path scriptFile = tempDir.resolve(fileName);
        
        // 写入脚本内容
        Files.write(scriptFile, request.getScriptContent().getBytes(request.getEncodingOrDefault()));
        
        // 设置执行权限（Unix系统）
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            scriptFile.toFile().setExecutable(true);
        }
        
        return scriptFile;
    }

    /**
     * 构建执行命令
     */
    private List<String> buildCommand(ScriptExecuteRequest request, Path scriptFile) {
        List<String> command = new ArrayList<>();
        ScriptExecuteRequest.ScriptType scriptType = request.getScriptTypeEnum();
        
        switch (scriptType) {
            case SHELL:
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    command.add("bash");
                } else {
                    command.add("/bin/bash");
                }
                command.add(scriptFile.toString());
                break;
            case BATCH:
                command.add("cmd");
                command.add("/c");
                command.add(scriptFile.toString());
                break;
            case POWERSHELL:
                command.add("powershell");
                command.add("-ExecutionPolicy");
                command.add("Bypass");
                command.add("-File");
                command.add(scriptFile.toString());
                break;
            case PYTHON:
                command.add("python");
                command.add(scriptFile.toString());
                break;
            case JAVASCRIPT:
                command.add("node");
                command.add(scriptFile.toString());
                break;
            default:
                throw new IllegalArgumentException("不支持的脚本类型: " + scriptType);
        }
        
        // 添加脚本参数
        if (request.getScriptParams() != null) {
            command.addAll(Arrays.asList(request.getScriptParams()));
        }
        
        return command;
    }

    /**
     * 读取输入流内容
     */
    private String readStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString();
        } catch (Exception e) {
            log.warn("读取流内容失败", e);
            return "";
        }
    }
}
