package com.chua.report.client.starter.service;

import com.chua.report.client.starter.pojo.ScriptExecuteRequest;
import com.chua.report.client.starter.pojo.ScriptExecuteResponse;

/**
 * 脚本执行服务接口
 * 
 * @author CH
 * @since 2024/12/19
 */
public interface ScriptExecuteService {

    /**
     * 执行脚本
     * 
     * @param request 脚本执行请求
     * @return 执行结果
     */
    ScriptExecuteResponse executeScript(ScriptExecuteRequest request);

    /**
     * 执行Shell脚本
     * 
     * @param scriptContent 脚本内容
     * @return 执行结果
     */
    ScriptExecuteResponse executeShellScript(String scriptContent);

    /**
     * 执行PowerShell脚本
     * 
     * @param scriptContent 脚本内容
     * @return 执行结果
     */
    ScriptExecuteResponse executePowerShellScript(String scriptContent);

    /**
     * 执行批处理脚本
     * 
     * @param scriptContent 脚本内容
     * @return 执行结果
     */
    ScriptExecuteResponse executeBatchScript(String scriptContent);

    /**
     * 执行Python脚本
     * 
     * @param scriptContent 脚本内容
     * @return 执行结果
     */
    ScriptExecuteResponse executePythonScript(String scriptContent);

    /**
     * 执行JavaScript脚本
     * 
     * @param scriptContent 脚本内容
     * @return 执行结果
     */
    ScriptExecuteResponse executeJavaScriptScript(String scriptContent);

    /**
     * 异步执行脚本
     * 
     * @param request 脚本执行请求
     * @return 执行结果（包含进程ID）
     */
    ScriptExecuteResponse executeScriptAsync(ScriptExecuteRequest request);

    /**
     * 停止脚本执行
     * 
     * @param processId 进程ID
     * @return 是否成功停止
     */
    boolean stopScript(Long processId);

    /**
     * 获取脚本执行状态
     * 
     * @param processId 进程ID
     * @return 执行状态
     */
    ScriptExecuteResponse getScriptStatus(Long processId);

    /**
     * 检查脚本类型是否支持
     * 
     * @param scriptType 脚本类型
     * @return 是否支持
     */
    boolean isScriptTypeSupported(String scriptType);

    /**
     * 获取支持的脚本类型列表
     * 
     * @return 脚本类型列表
     */
    java.util.List<String> getSupportedScriptTypes();
}
