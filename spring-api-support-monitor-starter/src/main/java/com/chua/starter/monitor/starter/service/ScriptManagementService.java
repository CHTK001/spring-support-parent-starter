package com.chua.starter.monitor.starter.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.starter.monitor.starter.entity.MonitorSysGenScript;
import com.chua.starter.monitor.starter.entity.MonitorSysGenScriptExecution;
import com.chua.starter.monitor.starter.pojo.ScriptManagementDTO;

import java.util.List;

/**
 * 脚本管理服务接口
 * 
 * @author CH
 * @since 2024/12/19
 */
public interface ScriptManagementService {

    /**
     * 分页查询脚本列表
     */
    IPage<ScriptManagementDTO> getScriptPage(ScriptManagementDTO.ScriptQueryDTO queryDTO);

    /**
     * 根据ID获取脚本详情
     */
    ScriptManagementDTO getScriptById(Integer scriptId);

    /**
     * 创建脚本
     */
    ScriptManagementDTO createScript(ScriptManagementDTO scriptDTO);

    /**
     * 更新脚本
     */
    ScriptManagementDTO updateScript(ScriptManagementDTO scriptDTO);

    /**
     * 删除脚本
     */
    boolean deleteScript(Integer scriptId);

    /**
     * 批量删除脚本
     */
    boolean deleteScripts(List<Integer> scriptIds);

    /**
     * 启用/禁用脚本
     */
    boolean updateScriptStatus(Integer scriptId, Integer status);

    /**
     * 复制脚本
     */
    ScriptManagementDTO copyScript(Integer scriptId, String newScriptName);

    /**
     * 执行脚本
     */
    ScriptManagementDTO.ScriptExecuteResponseDTO executeScript(ScriptManagementDTO.ScriptExecuteRequestDTO requestDTO);

    /**
     * 停止脚本执行
     */
    boolean stopScriptExecution(Long executionId);

    /**
     * 获取脚本执行历史
     */
    IPage<ScriptManagementDTO.ScriptExecuteResponseDTO> getExecutionHistory(Integer scriptId, Integer pageNum,
            Integer pageSize);

    /**
     * 获取脚本执行详情
     */
    ScriptManagementDTO.ScriptExecuteResponseDTO getExecutionDetail(Long executionId);

    /**
     * 获取正在运行的脚本
     */
    List<ScriptManagementDTO.ScriptExecuteResponseDTO> getRunningExecutions();

    /**
     * 获取脚本统计信息
     */
    ScriptManagementDTO.ScriptStatisticsDTO getScriptStatistics();

    /**
     * 获取支持的脚本类型
     */
    List<MonitorSysGenScript.ScriptType> getSupportedScriptTypes();

    /**
     * 验证脚本名称是否可用
     */
    boolean isScriptNameAvailable(String scriptName, Integer excludeId);

    /**
     * 根据分类获取脚本列表
     */
    List<ScriptManagementDTO> getScriptsByCategory(String category);

    /**
     * 根据类型获取脚本列表
     */
    List<ScriptManagementDTO> getScriptsByType(String scriptType);

    /**
     * 根据标签搜索脚本
     */
    List<ScriptManagementDTO> searchScriptsByTag(String tag);

    /**
     * 获取最近执行的脚本
     */
    List<ScriptManagementDTO> getRecentExecutedScripts(Integer limit);

    /**
     * 获取热门脚本(按执行次数排序)
     */
    List<ScriptManagementDTO> getPopularScripts(Integer limit);

    /**
     * 导出脚本
     */
    String exportScript(Integer scriptId);

    /**
     * 导入脚本
     */
    ScriptManagementDTO importScript(String scriptContent);

    /**
     * 验证脚本语法
     */
    boolean validateScriptSyntax(String scriptType, String scriptContent);

    /**
     * 获取脚本模板
     */
    String getScriptTemplate(String scriptType);

    /**
     * 清理过期的执行记录
     */
    int cleanExpiredExecutions(Integer days);
}
