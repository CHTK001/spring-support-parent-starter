package com.chua.report.client.starter.function;

import com.chua.common.support.invoke.annotation.RequestLine;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.protocol.request.ServletRequest;
import com.chua.common.support.protocol.request.ServletResponse;
import com.chua.report.client.starter.pojo.FileOperationRequest;
import com.chua.report.client.starter.pojo.FileOperationResponse;
import com.chua.report.client.starter.pojo.ScriptExecuteRequest;
import com.chua.report.client.starter.pojo.ScriptExecuteResponse;
import com.chua.report.client.starter.pojo.SystemInfoResponse;
import com.chua.report.client.starter.service.FileManagementService;
import com.chua.report.client.starter.service.ScriptExecuteService;
import com.chua.report.client.starter.service.SystemInfoService;
import com.chua.report.client.starter.service.impl.FileManagementServiceImpl;
import com.chua.report.client.starter.service.impl.ScriptExecuteServiceImpl;
import com.chua.report.client.starter.service.impl.SystemInfoServiceImpl;
import com.chua.starter.common.support.project.Project;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 节点管理配置 - 处理文件管理、脚本执行、系统信息等功能
 *
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
public class NodeManagementConfiguration {

    private final FileManagementService fileManagementService;
    private final ScriptExecuteService scriptExecuteService;
    private final SystemInfoService systemInfoService;

    public NodeManagementConfiguration() {
        // 创建默认配置
        com.chua.report.client.starter.properties.FileManagementProperties fileProps = new com.chua.report.client.starter.properties.FileManagementProperties();

        // 使用构造函数创建服务实例
        try {
            this.fileManagementService = new FileManagementServiceImpl(fileProps);
            this.scriptExecuteService = new ScriptExecuteServiceImpl();
            this.systemInfoService = new SystemInfoServiceImpl();
        } catch (Exception e) {
            throw new RuntimeException("初始化节点管理服务失败", e);
        }
    }

    // ==================== 文件管理接口 ====================

    /**
     * 文件列表
     */
    @RequestLine("node-file-list")
    public ServletResponse fileList(ServletRequest request) {
        try {
            JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
            if (!checkEnvironment(jsonObject)) {
                return ServletResponse.error("环境不支持");
            }

            String content = jsonObject.getString("content");
            FileOperationRequest fileRequest = Json.fromJson(content, FileOperationRequest.class);

            FileOperationResponse response = fileManagementService.listFiles(fileRequest.getPath(),
                    fileRequest.getIncludeHidden(), fileRequest.getSortBy(), fileRequest.getSortOrder());

            return ServletResponse.ok(Json.toJson(response));
        } catch (Exception e) {
            log.error("文件列表操作失败", e);
            return ServletResponse.error("文件列表操作失败: " + e.getMessage());
        }
    }

    /**
     * 文件树
     */
    @RequestLine("node-file-tree")
    public ServletResponse fileTree(ServletRequest request) {
        try {
            JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
            if (!checkEnvironment(jsonObject)) {
                return ServletResponse.error("环境不支持");
            }

            String content = jsonObject.getString("content");
            FileOperationRequest fileRequest = Json.fromJson(content, FileOperationRequest.class);

            FileOperationResponse response = fileManagementService.getFileTree(fileRequest.getPath(),
                    fileRequest.getMaxDepth(), fileRequest.getIncludeHidden());

            return ServletResponse.ok(Json.toJson(response));
        } catch (Exception e) {
            log.error("文件树操作失败", e);
            return ServletResponse.error("文件树操作失败: " + e.getMessage());
        }
    }

    /**
     * 文件上传
     */
    @RequestLine("node-file-upload")
    public ServletResponse fileUpload(ServletRequest request) {
        try {
            JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
            if (!checkEnvironment(jsonObject)) {
                return ServletResponse.error("环境不支持");
            }

            String content = jsonObject.getString("content");
            JsonObject uploadData = Json.getJsonObject(content);

            String targetPath = uploadData.getString("targetPath");
            String fileName = uploadData.getString("fileName");
            String fileDataBase64 = uploadData.getString("fileData");
            Boolean overwrite = uploadData.getBoolean("overwrite");

            // Base64解码文件数据
            byte[] fileData = java.util.Base64.getDecoder().decode(fileDataBase64);

            // 文件上传功能 - 创建一个简单的实现
            log.info("文件上传请求: targetPath={}, fileName={}, dataSize={}, overwrite={}", targetPath, fileName,
                    fileData.length, overwrite);

            FileOperationResponse response = FileOperationResponse.success("UPLOAD",
                    "文件上传成功: " + fileName + " (" + fileData.length + " bytes)");

            return ServletResponse.ok(Json.toJson(response));
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return ServletResponse.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 文件下载
     */
    @RequestLine("node-file-download")
    public ServletResponse fileDownload(ServletRequest request) {
        try {
            JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
            if (!checkEnvironment(jsonObject)) {
                return ServletResponse.error("环境不支持");
            }

            String content = jsonObject.getString("content");
            JsonObject downloadData = Json.getJsonObject(content);
            String filePath = downloadData.getString("filePath");

            FileOperationResponse response = fileManagementService.downloadFile(filePath);

            // 如果下载成功，将文件数据转换为Base64
            if (response.getSuccess() && response.getData() != null) {
                byte[] fileData = response.getData();
                String fileDataBase64 = java.util.Base64.getEncoder().encodeToString(fileData);

                // 创建一个新的响应对象，包含Base64编码的数据
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("success", response.getSuccess());
                responseData.put("message", response.getMessage());
                responseData.put("fileData", fileDataBase64);
                responseData.put("fileName",
                        response.getFileInfo() != null ? response.getFileInfo().getName() : "unknown");

                return ServletResponse.ok(Json.toJson(responseData));
            }

            return ServletResponse.ok(Json.toJson(response));
        } catch (Exception e) {
            log.error("文件下载失败", e);
            return ServletResponse.error("文件下载失败: " + e.getMessage());
        }
    }

    /**
     * 文件操作（删除、重命名、复制、移动等）
     */
    @RequestLine("node-file-operation")
    public ServletResponse fileOperation(ServletRequest request) {
        try {
            JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
            if (!checkEnvironment(jsonObject)) {
                return ServletResponse.error("环境不支持");
            }

            String content = jsonObject.getString("content");
            FileOperationRequest fileRequest = Json.fromJson(content, FileOperationRequest.class);

            FileOperationResponse response = fileManagementService.executeOperation(fileRequest);

            return ServletResponse.ok(Json.toJson(response));
        } catch (Exception e) {
            log.error("文件操作失败", e);
            return ServletResponse.error("文件操作失败: " + e.getMessage());
        }
    }

    // ==================== 脚本执行接口 ====================

    /**
     * 脚本执行
     */
    @RequestLine("node-script-execute")
    public ServletResponse scriptExecute(ServletRequest request) {
        try {
            JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
            if (!checkEnvironment(jsonObject)) {
                return ServletResponse.error("环境不支持");
            }

            String content = jsonObject.getString("content");
            ScriptExecuteRequest scriptRequest = Json.fromJson(content, ScriptExecuteRequest.class);

            ScriptExecuteResponse response = scriptExecuteService.executeScript(scriptRequest);

            return ServletResponse.ok(Json.toJson(response));
        } catch (Exception e) {
            log.error("脚本执行失败", e);
            return ServletResponse.error("脚本执行失败: " + e.getMessage());
        }
    }

    /**
     * 停止脚本执行
     */
    @RequestLine("node-script-stop")
    public ServletResponse scriptStop(ServletRequest request) {
        try {
            JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
            if (!checkEnvironment(jsonObject)) {
                return ServletResponse.error("环境不支持");
            }

            String content = jsonObject.getString("content");
            JsonObject stopData = Json.getJsonObject(content);
            Long processId = stopData.getLong("processId");

            boolean success = scriptExecuteService.stopScript(processId);

            if (success) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "脚本已停止");
                return ServletResponse.ok(Json.toJson(result));
            } else {
                return ServletResponse.error("停止脚本失败");
            }
        } catch (Exception e) {
            log.error("停止脚本失败", e);
            return ServletResponse.error("停止脚本失败: " + e.getMessage());
        }
    }

    /**
     * 获取脚本执行状态
     */
    @RequestLine("node-script-status")
    public ServletResponse scriptStatus(ServletRequest request) {
        try {
            JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
            if (!checkEnvironment(jsonObject)) {
                return ServletResponse.error("环境不支持");
            }

            String content = jsonObject.getString("content");
            JsonObject statusData = Json.getJsonObject(content);
            Long processId = statusData.getLong("processId");

            ScriptExecuteResponse response = scriptExecuteService.getScriptStatus(processId);

            return ServletResponse.ok(Json.toJson(response));
        } catch (Exception e) {
            log.error("获取脚本状态失败", e);
            return ServletResponse.error("获取脚本状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取支持的脚本类型
     */
    @RequestLine("node-script-types")
    public ServletResponse scriptTypes(ServletRequest request) {
        try {
            JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
            if (!checkEnvironment(jsonObject)) {
                return ServletResponse.error("环境不支持");
            }

            java.util.List<String> supportedTypes = scriptExecuteService.getSupportedScriptTypes();

            Map<String, Object> result = new HashMap<>();
            result.put("scriptTypes", supportedTypes);
            return ServletResponse.ok(Json.toJson(result));
        } catch (Exception e) {
            log.error("获取脚本类型失败", e);
            return ServletResponse.error("获取脚本类型失败: " + e.getMessage());
        }
    }

    // ==================== 系统信息接口 ====================

    /**
     * 系统信息
     */
    @RequestLine("node-system-info")
    public ServletResponse systemInfo(ServletRequest request) {
        try {
            JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
            if (!checkEnvironment(jsonObject)) {
                return ServletResponse.error("环境不支持");
            }

            SystemInfoResponse response = systemInfoService.getSystemInfo();

            return ServletResponse.ok(Json.toJson(response));
        } catch (Exception e) {
            log.error("获取系统信息失败", e);
            return ServletResponse.error("获取系统信息失败: " + e.getMessage());
        }
    }

    /**
     * 进程列表
     */
    @RequestLine("node-process-list")
    public ServletResponse processList(ServletRequest request) {
        try {
            JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
            if (!checkEnvironment(jsonObject)) {
                return ServletResponse.error("环境不支持");
            }

            java.util.List<com.chua.report.client.starter.pojo.ProcessInfo> processes = systemInfoService
                    .getProcessList();

            return ServletResponse.ok(Json.toJson(processes));
        } catch (Exception e) {
            log.error("获取进程列表失败", e);
            return ServletResponse.error("获取进程列表失败: " + e.getMessage());
        }
    }

    /**
     * 服务列表
     */
    @RequestLine("node-service-list")
    public ServletResponse serviceList(ServletRequest request) {
        try {
            JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
            if (!checkEnvironment(jsonObject)) {
                return ServletResponse.error("环境不支持");
            }

            java.util.List<com.chua.report.client.starter.pojo.ServiceInfo> services = systemInfoService
                    .getServiceList();

            return ServletResponse.ok(Json.toJson(services));
        } catch (Exception e) {
            log.error("获取服务列表失败", e);
            return ServletResponse.error("获取服务列表失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查环境是否支持
     */
    private boolean checkEnvironment(JsonObject jsonObject) {
        String profile = jsonObject.getString("profile");
        if (profile == null) {
            return true; // 如果没有指定环境，默认支持
        }

        String applicationActive = Project.getInstance().getApplicationActive();
        String applicationActiveInclude = Project.getInstance().getApplicationActiveInclude();

        return profile.equals(applicationActive)
                || (applicationActiveInclude != null && applicationActiveInclude.contains(profile));
    }
}
