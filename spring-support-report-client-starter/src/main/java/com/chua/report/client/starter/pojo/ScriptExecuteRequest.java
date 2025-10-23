package com.chua.report.client.starter.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Map;

/**
 * 脚本执行请求
 * @author CH
 * @since 2024/12/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptExecuteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 脚本类型
     */
    @NotBlank(message = "脚本类型不能为空")
    private String scriptType;

    /**
     * 脚本内容
     */
    @NotBlank(message = "脚本内容不能为空")
    private String scriptContent;

    /**
     * 脚本参数
     */
    private String[] scriptParams;

    /**
     * 工作目录
     */
    private String workingDirectory;

    /**
     * 超时时间（秒）
     */
    private Integer timeout;

    /**
     * 环境变量
     */
    private Map<String, String> environment;

    /**
     * 是否异步执行
     */
    private Boolean async;

    /**
     * 执行用户
     */
    private String executeUser;

    /**
     * 脚本编码
     */
    private String encoding;

    /**
     * 脚本类型枚举
     */
    public enum ScriptType {
        SHELL("shell", "Shell脚本", ".sh"),
        BATCH("batch", "批处理脚本", ".bat"),
        POWERSHELL("powershell", "PowerShell脚本", ".ps1"),
        PYTHON("python", "Python脚本", ".py"),
        JAVASCRIPT("javascript", "JavaScript脚本", ".js"),
        GROOVY("groovy", "Groovy脚本", ".groovy"),
        LUA("lua", "Lua脚本", ".lua"),
        PERL("perl", "Perl脚本", ".pl"),
        RUBY("ruby", "Ruby脚本", ".rb"),
        PHP("php", "PHP脚本", ".php");

        private final String code;
        private final String desc;
        private final String extension;

        ScriptType(String code, String desc, String extension) {
            this.code = code;
            this.desc = desc;
            this.extension = extension;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public String getExtension() {
            return extension;
        }

        public static ScriptType fromCode(String code) {
            for (ScriptType type : values()) {
                if (type.code.equalsIgnoreCase(code)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 获取脚本类型枚举
     */
    public ScriptType getScriptTypeEnum() {
        return ScriptType.fromCode(scriptType);
    }

    /**
     * 验证请求参数
     */
    public String getValidationError() {
        if (scriptType == null || scriptType.trim().isEmpty()) {
            return "脚本类型不能为空";
        }
        
        if (scriptContent == null || scriptContent.trim().isEmpty()) {
            return "脚本内容不能为空";
        }
        
        ScriptType type = getScriptTypeEnum();
        if (type == null) {
            return "不支持的脚本类型: " + scriptType;
        }
        
        if (timeout != null && timeout <= 0) {
            return "超时时间必须大于0";
        }
        
        return null;
    }

    /**
     * 验证请求参数，如果无效则抛出异常
     */
    public void validate() {
        String error = getValidationError();
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }

    /**
     * 创建Shell脚本执行请求
     */
    public static ScriptExecuteRequest createShellScript(String scriptContent) {
        return ScriptExecuteRequest.builder()
                .scriptType("shell")
                .scriptContent(scriptContent)
                .build();
    }

    /**
     * 创建PowerShell脚本执行请求
     */
    public static ScriptExecuteRequest createPowerShellScript(String scriptContent) {
        return ScriptExecuteRequest.builder()
                .scriptType("powershell")
                .scriptContent(scriptContent)
                .build();
    }

    /**
     * 创建Python脚本执行请求
     */
    public static ScriptExecuteRequest createPythonScript(String scriptContent) {
        return ScriptExecuteRequest.builder()
                .scriptType("python")
                .scriptContent(scriptContent)
                .build();
    }

    /**
     * 创建批处理脚本执行请求
     */
    public static ScriptExecuteRequest createBatchScript(String scriptContent) {
        return ScriptExecuteRequest.builder()
                .scriptType("batch")
                .scriptContent(scriptContent)
                .build();
    }

    /**
     * 设置脚本参数
     */
    public ScriptExecuteRequest withParams(String... params) {
        this.scriptParams = params;
        return this;
    }

    /**
     * 设置工作目录
     */
    public ScriptExecuteRequest withWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
        return this;
    }

    /**
     * 设置超时时间
     */
    public ScriptExecuteRequest withTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * 设置环境变量
     */
    public ScriptExecuteRequest withEnvironment(Map<String, String> environment) {
        this.environment = environment;
        return this;
    }

    /**
     * 设置异步执行
     */
    public ScriptExecuteRequest withAsync(boolean async) {
        this.async = async;
        return this;
    }

    /**
     * 设置执行用户
     */
    public ScriptExecuteRequest withExecuteUser(String executeUser) {
        this.executeUser = executeUser;
        return this;
    }

    /**
     * 设置脚本编码
     */
    public ScriptExecuteRequest withEncoding(String encoding) {
        this.encoding = encoding;
        return this;
    }

    /**
     * 获取默认超时时间
     */
    public int getTimeoutOrDefault() {
        return timeout != null ? timeout : 300; // 默认5分钟
    }

    /**
     * 获取默认编码
     */
    public String getEncodingOrDefault() {
        return encoding != null ? encoding : "UTF-8";
    }

    /**
     * 获取默认工作目录
     */
    public String getWorkingDirectoryOrDefault() {
        return workingDirectory != null ? workingDirectory : System.getProperty("login.dir");
    }

    /**
     * 是否异步执行
     */
    public boolean isAsync() {
        return Boolean.TRUE.equals(async);
    }
}
