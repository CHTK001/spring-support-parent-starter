package com.chua.starter.monitor.starter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.monitor.starter.entity.MonitorSysGenScript;
import com.chua.starter.monitor.starter.entity.MonitorSysGenScriptExecution;
import com.chua.starter.monitor.starter.mapper.MonitorSysGenScriptMapper;
import com.chua.starter.monitor.starter.mapper.MonitorSysGenScriptExecutionMapper;
import com.chua.starter.monitor.starter.pojo.ScriptManagementDTO;
import com.chua.starter.monitor.starter.service.ScriptManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 脚本管理服务实现
 * 
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptManagementServiceImpl implements ScriptManagementService {

    private final MonitorSysGenScriptMapper scriptMapper;
    private final MonitorSysGenScriptExecutionMapper executionMapper;

    @Override
    public IPage<ScriptManagementDTO> getScriptPage(ScriptManagementDTO.ScriptQueryDTO queryDTO) {
        // 构建查询条件
        LambdaQueryWrapper<MonitorSysGenScript> queryWrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(queryDTO.getScriptName())) {
            queryWrapper.like(MonitorSysGenScript::getScriptName, queryDTO.getScriptName());
        }
        if (StringUtils.hasText(queryDTO.getScriptType())) {
            queryWrapper.eq(MonitorSysGenScript::getScriptType, queryDTO.getScriptType());
        }
        if (StringUtils.hasText(queryDTO.getScriptCategory())) {
            queryWrapper.eq(MonitorSysGenScript::getScriptCategory, queryDTO.getScriptCategory());
        }
        if (queryDTO.getScriptStatus() != null) {
            queryWrapper.eq(MonitorSysGenScript::getScriptStatus,
                    MonitorSysGenScript.ScriptStatus.fromCode(queryDTO.getScriptStatus()));
        }
        if (StringUtils.hasText(queryDTO.getScriptAuthor())) {
            queryWrapper.like(MonitorSysGenScript::getScriptAuthor, queryDTO.getScriptAuthor());
        }
        if (StringUtils.hasText(queryDTO.getScriptTag())) {
            queryWrapper.like(MonitorSysGenScript::getScriptTags, queryDTO.getScriptTag());
        }
        if (queryDTO.getCreateTimeStart() != null) {
            queryWrapper.ge(MonitorSysGenScript::getCreateTime, queryDTO.getCreateTimeStart());
        }
        if (queryDTO.getCreateTimeEnd() != null) {
            queryWrapper.le(MonitorSysGenScript::getCreateTime, queryDTO.getCreateTimeEnd());
        }

        // 排序
        String sortBy = queryDTO.getSortBy();
        String sortOrder = queryDTO.getSortOrder();
        if (StringUtils.hasText(sortBy)) {
            boolean isAsc = !"desc".equalsIgnoreCase(sortOrder);
            switch (sortBy) {
            case "scriptName":
                queryWrapper.orderBy(true, isAsc, MonitorSysGenScript::getScriptName);
                break;
            case "scriptType":
                queryWrapper.orderBy(true, isAsc, MonitorSysGenScript::getScriptType);
                break;
            case "createTime":
                queryWrapper.orderBy(true, isAsc, MonitorSysGenScript::getCreateTime);
                break;
            case "updateTime":
                queryWrapper.orderBy(true, isAsc, MonitorSysGenScript::getUpdateTime);
                break;
            case "executeCount":
                queryWrapper.orderBy(true, isAsc, MonitorSysGenScript::getScriptExecuteCount);
                break;
            default:
                queryWrapper.orderByDesc(MonitorSysGenScript::getUpdateTime);
            }
        } else {
            queryWrapper.orderByDesc(MonitorSysGenScript::getUpdateTime);
        }

        // 分页查询
        Page<MonitorSysGenScript> page = new Page<>(queryDTO.getPageNum() != null ? queryDTO.getPageNum() : 1,
                queryDTO.getPageSize() != null ? queryDTO.getPageSize() : 10);

        IPage<MonitorSysGenScript> scriptPage = scriptMapper.selectPage(page, queryWrapper);

        // 转换为DTO
        return scriptPage.convert(this::convertToDTO);
    }

    @Override
    public ScriptManagementDTO getScriptById(Integer scriptId) {
        MonitorSysGenScript script = scriptMapper.selectById(scriptId);
        return script != null ? convertToDTO(script) : null;
    }

    @Override
    @Transactional
    public ScriptManagementDTO createScript(ScriptManagementDTO scriptDTO) {
        // 验证脚本名称唯一性
        if (!isScriptNameAvailable(scriptDTO.getScriptName(), null)) {
            throw new IllegalArgumentException("脚本名称已存在: " + scriptDTO.getScriptName());
        }

        MonitorSysGenScript script = new MonitorSysGenScript();
        BeanUtils.copyProperties(scriptDTO, script);

        // 设置默认值
        script.setScriptStatus(MonitorSysGenScript.ScriptStatus.ENABLED);
        script.setScriptVersion("1.0.0");
        script.setScriptExecuteCount(0);
        script.setCreateTime(LocalDateTime.now());
        script.setUpdateTime(LocalDateTime.now());

        // 处理标签
        if (scriptDTO.getScriptTags() != null) {
            script.setTagArray(scriptDTO.getScriptTags());
        }

        scriptMapper.insert(script);

        log.info("创建脚本成功: scriptId={}, scriptName={}", script.getScriptId(), script.getScriptName());
        return convertToDTO(script);
    }

    @Override
    @Transactional
    public ScriptManagementDTO updateScript(ScriptManagementDTO scriptDTO) {
        MonitorSysGenScript existingScript = scriptMapper.selectById(scriptDTO.getScriptId());
        if (existingScript == null) {
            throw new IllegalArgumentException("脚本不存在: " + scriptDTO.getScriptId());
        }

        // 验证脚本名称唯一性
        if (!isScriptNameAvailable(scriptDTO.getScriptName(), scriptDTO.getScriptId())) {
            throw new IllegalArgumentException("脚本名称已存在: " + scriptDTO.getScriptName());
        }

        // 更新脚本信息
        BeanUtils.copyProperties(scriptDTO, existingScript, "scriptId", "createTime", "createBy", "scriptExecuteCount",
                "scriptLastExecuteTime");
        existingScript.setUpdateTime(LocalDateTime.now());

        // 处理标签
        if (scriptDTO.getScriptTags() != null) {
            existingScript.setTagArray(scriptDTO.getScriptTags());
        }

        scriptMapper.updateById(existingScript);

        log.info("更新脚本成功: scriptId={}, scriptName={}", existingScript.getScriptId(), existingScript.getScriptName());
        return convertToDTO(existingScript);
    }

    @Override
    @Transactional
    public boolean deleteScript(Integer scriptId) {
        MonitorSysGenScript script = scriptMapper.selectById(scriptId);
        if (script == null) {
            return false;
        }

        // 删除脚本及其执行历史
        scriptMapper.deleteById(scriptId);

        // 删除相关执行记录
        LambdaQueryWrapper<MonitorSysGenScriptExecution> executionWrapper = new LambdaQueryWrapper<>();
        executionWrapper.eq(MonitorSysGenScriptExecution::getScriptId, scriptId);
        executionMapper.delete(executionWrapper);

        log.info("删除脚本成功: scriptId={}, scriptName={}", scriptId, script.getScriptName());
        return true;
    }

    @Override
    @Transactional
    public boolean deleteScripts(List<Integer> scriptIds) {
        if (scriptIds == null || scriptIds.isEmpty()) {
            return false;
        }

        for (Integer scriptId : scriptIds) {
            deleteScript(scriptId);
        }

        log.info("批量删除脚本成功: count={}", scriptIds.size());
        return true;
    }

    @Override
    @Transactional
    public boolean updateScriptStatus(Integer scriptId, Integer status) {
        MonitorSysGenScript script = scriptMapper.selectById(scriptId);
        if (script == null) {
            return false;
        }

        MonitorSysGenScript.ScriptStatus scriptStatus = MonitorSysGenScript.ScriptStatus.fromCode(status);
        if (scriptStatus == null) {
            throw new IllegalArgumentException("无效的脚本状态: " + status);
        }

        script.setScriptStatus(scriptStatus);
        script.setUpdateTime(LocalDateTime.now());

        scriptMapper.updateById(script);

        log.info("更新脚本状态成功: scriptId={}, status={}", scriptId, scriptStatus.getDesc());
        return true;
    }

    @Override
    @Transactional
    public ScriptManagementDTO copyScript(Integer scriptId, String newScriptName) {
        MonitorSysGenScript originalScript = scriptMapper.selectById(scriptId);
        if (originalScript == null) {
            throw new IllegalArgumentException("原脚本不存在: " + scriptId);
        }

        // 验证新脚本名称唯一性
        if (!isScriptNameAvailable(newScriptName, null)) {
            throw new IllegalArgumentException("脚本名称已存在: " + newScriptName);
        }

        MonitorSysGenScript newScript = new MonitorSysGenScript();
        BeanUtils.copyProperties(originalScript, newScript, "scriptId", "scriptName", "createTime", "updateTime",
                "createBy", "updateBy", "scriptExecuteCount", "scriptLastExecuteTime");

        newScript.setScriptName(newScriptName);
        newScript.setScriptExecuteCount(0);
        newScript.setScriptLastExecuteTime(null);
        newScript.setCreateTime(LocalDateTime.now());
        newScript.setUpdateTime(LocalDateTime.now());

        scriptMapper.insert(newScript);

        log.info("复制脚本成功: originalId={}, newId={}, newName={}", scriptId, newScript.getScriptId(), newScriptName);
        return convertToDTO(newScript);
    }

    /**
     * 转换实体为DTO
     */
    private ScriptManagementDTO convertToDTO(MonitorSysGenScript script) {
        ScriptManagementDTO dto = new ScriptManagementDTO();
        BeanUtils.copyProperties(script, dto);

        // 处理枚举转换
        if (script.getScriptStatus() != null) {
            dto.setScriptStatus(script.getScriptStatus().getCode());
        }

        // 处理标签
        dto.setScriptTags(script.getTagArray());

        return dto;
    }

    @Override
    public boolean isScriptNameAvailable(String scriptName, Integer excludeId) {
        LambdaQueryWrapper<MonitorSysGenScript> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MonitorSysGenScript::getScriptName, scriptName);
        if (excludeId != null) {
            queryWrapper.ne(MonitorSysGenScript::getScriptId, excludeId);
        }

        return scriptMapper.selectCount(queryWrapper) == 0;
    }

    @Override
    public List<MonitorSysGenScript.ScriptType> getSupportedScriptTypes() {
        return Arrays.asList(MonitorSysGenScript.ScriptType.values());
    }

    @Override
    public ScriptManagementDTO.ScriptExecuteResponseDTO executeScript(
            ScriptManagementDTO.ScriptExecuteRequestDTO requestDTO) {
        // 获取脚本信息
        MonitorSysGenScript script = scriptMapper.selectById(requestDTO.getScriptId());
        if (script == null) {
            throw new IllegalArgumentException("脚本不存在: " + requestDTO.getScriptId());
        }

        if (!script.isEnabled()) {
            throw new IllegalArgumentException("脚本已禁用: " + script.getScriptName());
        }

        // 创建执行记录
        MonitorSysGenScriptExecution execution = new MonitorSysGenScriptExecution();
        execution.setScriptId(script.getScriptId());
        execution.setExecutionParameters(
                requestDTO.getParameters() != null ? String.join(" ", requestDTO.getParameters()) : null);
        execution.setExecutionStatus(MonitorSysGenScriptExecution.ExecutionStatus.RUNNING);
        execution.setExecutionStartTime(LocalDateTime.now());
        execution.setExecutionServerId(requestDTO.getServerId());
        execution.setExecutionTriggerType(
                MonitorSysGenScriptExecution.TriggerType.fromCode(requestDTO.getTriggerType()));
        execution.setCreateTime(LocalDateTime.now());

        executionMapper.insert(execution);

        // 更新脚本执行统计
        script.incrementExecuteCount();
        scriptMapper.updateById(script);

        // TODO: 集成实际的脚本执行服务
        // 这里应该调用NodeManagementConfiguration中的脚本执行功能

        log.info("开始执行脚本: scriptId={}, scriptName={}, executionId={}", script.getScriptId(), script.getScriptName(),
                execution.getExecutionId());

        return convertExecutionToDTO(execution, script);
    }

    @Override
    public boolean stopScriptExecution(Long executionId) {
        MonitorSysGenScriptExecution execution = executionMapper.selectById(executionId);
        if (execution == null || !execution.isRunning()) {
            return false;
        }

        // TODO: 调用实际的停止脚本功能

        execution.setExecutionStatus(MonitorSysGenScriptExecution.ExecutionStatus.CANCELLED);
        execution.setExecutionEndTime(LocalDateTime.now());
        execution.calculateDuration();

        executionMapper.updateById(execution);

        log.info("停止脚本执行: executionId={}", executionId);
        return true;
    }

    @Override
    public IPage<ScriptManagementDTO.ScriptExecuteResponseDTO> getExecutionHistory(Integer scriptId, Integer pageNum,
            Integer pageSize) {
        LambdaQueryWrapper<MonitorSysGenScriptExecution> queryWrapper = new LambdaQueryWrapper<>();
        if (scriptId != null) {
            queryWrapper.eq(MonitorSysGenScriptExecution::getScriptId, scriptId);
        }
        queryWrapper.orderByDesc(MonitorSysGenScriptExecution::getExecutionStartTime);

        Page<MonitorSysGenScriptExecution> page = new Page<>(pageNum != null ? pageNum : 1,
                pageSize != null ? pageSize : 10);

        IPage<MonitorSysGenScriptExecution> executionPage = executionMapper.selectPage(page, queryWrapper);

        return executionPage.convert(execution -> {
            MonitorSysGenScript script = scriptMapper.selectById(execution.getScriptId());
            return convertExecutionToDTO(execution, script);
        });
    }

    @Override
    public ScriptManagementDTO.ScriptExecuteResponseDTO getExecutionDetail(Long executionId) {
        MonitorSysGenScriptExecution execution = executionMapper.selectById(executionId);
        if (execution == null) {
            return null;
        }

        MonitorSysGenScript script = scriptMapper.selectById(execution.getScriptId());
        return convertExecutionToDTO(execution, script);
    }

    @Override
    public List<ScriptManagementDTO.ScriptExecuteResponseDTO> getRunningExecutions() {
        List<MonitorSysGenScriptExecution> runningExecutions = executionMapper.findRunningExecutions();

        return runningExecutions.stream().map(execution -> {
            MonitorSysGenScript script = scriptMapper.selectById(execution.getScriptId());
            return convertExecutionToDTO(execution, script);
        }).collect(Collectors.toList());
    }

    /**
     * 转换执行记录为DTO
     */
    private ScriptManagementDTO.ScriptExecuteResponseDTO convertExecutionToDTO(MonitorSysGenScriptExecution execution,
            MonitorSysGenScript script) {
        ScriptManagementDTO.ScriptExecuteResponseDTO dto = new ScriptManagementDTO.ScriptExecuteResponseDTO();
        BeanUtils.copyProperties(execution, dto);

        dto.setExecutionId(execution.getExecutionId());
        dto.setScriptId(execution.getScriptId());
        dto.setScriptName(script != null ? script.getScriptName() : "未知脚本");
        dto.setExecutionStatus(
                execution.getExecutionStatus() != null ? execution.getExecutionStatus().getCode() : null);
        dto.setStartTime(execution.getExecutionStartTime());
        dto.setEndTime(execution.getExecutionEndTime());
        dto.setDuration(execution.getExecutionDuration());
        dto.setExitCode(execution.getExecutionExitCode());
        dto.setStdout(execution.getExecutionStdout());
        dto.setStderr(execution.getExecutionStderr());
        dto.setErrorMessage(execution.getExecutionErrorMessage());
        dto.setProcessId(execution.getExecutionProcessId());
        dto.setServerId(execution.getExecutionServerId());
        dto.setTriggerType(
                execution.getExecutionTriggerType() != null ? execution.getExecutionTriggerType().getCode() : null);
        dto.setExecuteBy(execution.getCreateBy());

        return dto;
    }

    @Override
    public ScriptManagementDTO.ScriptStatisticsDTO getScriptStatistics() {
        // 获取脚本统计
        MonitorSysGenScriptMapper.ScriptStatistics scriptStats = scriptMapper.getScriptStatistics();

        // 获取类型统计
        List<MonitorSysGenScriptMapper.ScriptTypeCount> typeStats = scriptMapper.getScriptTypeStatistics();
        List<ScriptManagementDTO.ScriptTypeStatistics> typeStatistics = typeStats.stream()
                .map(stat -> ScriptManagementDTO.ScriptTypeStatistics.builder().scriptType(stat.getScriptType())
                        .scriptTypeDesc(MonitorSysGenScript.ScriptType.fromCode(stat.getScriptType()).getDesc())
                        .count(stat.getCount()).build())
                .collect(Collectors.toList());

        // 获取分类统计
        List<MonitorSysGenScriptMapper.ScriptCategoryCount> categoryStats = scriptMapper.getScriptCategoryStatistics();
        List<ScriptManagementDTO.ScriptCategoryStatistics> categoryStatistics = categoryStats.stream()
                .map(stat -> ScriptManagementDTO.ScriptCategoryStatistics.builder().category(stat.getCategory())
                        .count(stat.getCount()).build())
                .collect(Collectors.toList());

        // 获取执行统计
        MonitorSysGenScriptExecutionMapper.ExecutionStatistics execStats = executionMapper.getExecutionStatistics();
        ScriptManagementDTO.ExecutionStatistics executionStatistics = ScriptManagementDTO.ExecutionStatistics.builder()
                .totalExecutions(execStats.getTotalExecutions()).successCount(execStats.getSuccessCount())
                .failedCount(execStats.getFailedCount()).timeoutCount(execStats.getTimeoutCount())
                .runningCount(execStats.getRunningCount()).avgDuration(execStats.getAvgDuration())
                .successRate(execStats.getTotalExecutions() > 0
                        ? (double) execStats.getSuccessCount() / execStats.getTotalExecutions() * 100
                        : 0.0)
                .build();

        return ScriptManagementDTO.ScriptStatisticsDTO.builder().totalCount(scriptStats.getTotalCount())
                .enabledCount(scriptStats.getEnabledCount()).disabledCount(scriptStats.getDisabledCount())
                .totalExecutions(scriptStats.getTotalExecutions()).typeStatistics(typeStatistics)
                .categoryStatistics(categoryStatistics).executionStatistics(executionStatistics).build();
    }

    @Override
    public List<ScriptManagementDTO> getScriptsByCategory(String category) {
        List<MonitorSysGenScript> scripts = scriptMapper.findByCategory(category);
        return scripts.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<ScriptManagementDTO> getScriptsByType(String scriptType) {
        List<MonitorSysGenScript> scripts = scriptMapper.findByScriptType(scriptType);
        return scripts.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<ScriptManagementDTO> searchScriptsByTag(String tag) {
        List<MonitorSysGenScript> scripts = scriptMapper.findByTag(tag);
        return scripts.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<ScriptManagementDTO> getRecentExecutedScripts(Integer limit) {
        LambdaQueryWrapper<MonitorSysGenScript> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.isNotNull(MonitorSysGenScript::getScriptLastExecuteTime)
                .eq(MonitorSysGenScript::getScriptStatus, MonitorSysGenScript.ScriptStatus.ENABLED)
                .orderByDesc(MonitorSysGenScript::getScriptLastExecuteTime)
                .last("LIMIT " + (limit != null ? limit : 10));

        List<MonitorSysGenScript> scripts = scriptMapper.selectList(queryWrapper);
        return scripts.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<ScriptManagementDTO> getPopularScripts(Integer limit) {
        LambdaQueryWrapper<MonitorSysGenScript> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.gt(MonitorSysGenScript::getScriptExecuteCount, 0)
                .eq(MonitorSysGenScript::getScriptStatus, MonitorSysGenScript.ScriptStatus.ENABLED)
                .orderByDesc(MonitorSysGenScript::getScriptExecuteCount).last("LIMIT " + (limit != null ? limit : 10));

        List<MonitorSysGenScript> scripts = scriptMapper.selectList(queryWrapper);
        return scripts.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public String exportScript(Integer scriptId) {
        MonitorSysGenScript script = scriptMapper.selectById(scriptId);
        if (script == null) {
            throw new IllegalArgumentException("脚本不存在: " + scriptId);
        }

        // 构建导出JSON
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("scriptName", script.getScriptName());
        exportData.put("scriptType", script.getScriptType());
        exportData.put("scriptDescription", script.getScriptDescription());
        exportData.put("scriptContent", script.getScriptContent());
        exportData.put("scriptParameters", script.getScriptParameters());
        exportData.put("scriptTimeout", script.getScriptTimeout());
        exportData.put("scriptTags", script.getScriptTags());
        exportData.put("scriptCategory", script.getScriptCategory());
        exportData.put("scriptVersion", script.getScriptVersion());
        exportData.put("scriptAuthor", script.getScriptAuthor());
        exportData.put("exportTime", LocalDateTime.now());

        // 转换为JSON字符串
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(exportData);
        } catch (Exception e) {
            throw new RuntimeException("导出脚本失败", e);
        }
    }

    @Override
    @Transactional
    public ScriptManagementDTO importScript(String scriptContent) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> importData = mapper.readValue(scriptContent, Map.class);

            ScriptManagementDTO scriptDTO = ScriptManagementDTO.builder()
                    .scriptName((String) importData.get("scriptName")).scriptType((String) importData.get("scriptType"))
                    .scriptDescription((String) importData.get("scriptDescription"))
                    .scriptContent((String) importData.get("scriptContent"))
                    .scriptParameters((String) importData.get("scriptParameters"))
                    .scriptTimeout((Integer) importData.get("scriptTimeout"))
                    .scriptCategory((String) importData.get("scriptCategory"))
                    .scriptVersion((String) importData.get("scriptVersion"))
                    .scriptAuthor((String) importData.get("scriptAuthor")).build();

            // 处理标签
            String tags = (String) importData.get("scriptTags");
            if (tags != null) {
                scriptDTO.setScriptTags(tags.split(","));
            }

            return createScript(scriptDTO);
        } catch (Exception e) {
            throw new RuntimeException("导入脚本失败", e);
        }
    }

    @Override
    public boolean validateScriptSyntax(String scriptType, String scriptContent) {
        // 基本的语法验证
        if (scriptContent == null || scriptContent.trim().isEmpty()) {
            return false;
        }

        MonitorSysGenScript.ScriptType type = MonitorSysGenScript.ScriptType.fromCode(scriptType);
        if (type == null) {
            return false;
        }

        // TODO: 实现具体的语法验证逻辑
        return true;
    }

    @Override
    public String getScriptTemplate(String scriptType) {
        MonitorSysGenScript.ScriptType type = MonitorSysGenScript.ScriptType.fromCode(scriptType);
        if (type == null) {
            return "";
        }

        switch (type) {
        case SHELL:
            return "#!/bin/bash\n\n# Shell脚本模板\necho \"Hello, World!\"\n";
        case PYTHON:
            return "#!/usr/bin/env python3\n# -*- coding: utf-8 -*-\n\n# Python脚本模板\nprint(\"Hello, World!\")\n";
        case JAVASCRIPT:
            return "// JavaScript脚本模板\nconsole.log(\"Hello, World!\");\n";
        case POWERSHELL:
            return "# PowerShell脚本模板\nWrite-Host \"Hello, World!\"\n";
        case BATCH:
            return "@echo off\nREM 批处理脚本模板\necho Hello, World!\n";
        default:
            return "# " + type.getDesc() + "脚本模板\n";
        }
    }

    @Override
    @Transactional
    public int cleanExpiredExecutions(Integer days) {
        LocalDateTime expireTime = LocalDateTime.now().minusDays(days != null ? days : 30);
        return executionMapper.cleanExpiredExecutions(expireTime);
    }
}
