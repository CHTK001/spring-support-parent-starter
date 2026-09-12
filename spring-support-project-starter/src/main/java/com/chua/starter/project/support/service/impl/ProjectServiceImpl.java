package com.chua.starter.project.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.project.support.constants.ProjectConstants;
import com.chua.starter.project.support.entity.MsProject;
import com.chua.starter.project.support.entity.MsProjectDeployLog;
import com.chua.starter.project.support.mapper.MsProjectDeployLogMapper;
import com.chua.starter.project.support.mapper.MsProjectMapper;
import com.chua.starter.project.support.service.ProjectService;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发布项目服务实现
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    /** 项目 映射器 */
    private final MsProjectMapper projectMapper;

    /** 部署日志 映射器（删除项目时级联清理） */
    private final MsProjectDeployLogMapper deployLogMapper;

    /** 服务器主机 映射器（来自 spring-支持-服务端-启动） */
    private final MsServerHostMapper serverHostMapper;

    /** Jackson */
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建。
     * @param project project
     * @return 创建的结果
     */
    public ReturnResult<MsProject> create(MsProject project) {
        ReturnResult<MsProject> validated = validate(project);
        if (validated != null) {
            return validated;
        }
        project.setId(null);
        project.setMsProjectRuntimeStatus(ProjectConstants.RUNTIME_UNKNOWN);
        project.setMsProjectSort(project.getMsProjectSort() == null ? 100 : project.getMsProjectSort());
        projectMapper.insert(project);
        return ReturnResult.ok(project, "创建成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新。
     * @param id 标识
     * @param project project
     * @return 更新的结果
     */
    public ReturnResult<MsProject> update(Long id, MsProject project) {
        if (id == null || projectMapper.selectById(id) == null) {
            return ReturnResult.error("项目不存在: " + id);
        }
        ReturnResult<MsProject> validated = validate(project);
        if (validated != null) {
            return validated;
        }
        project.setId(id);
        projectMapper.updateById(project);
        return ReturnResult.ok(project, "更新成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除。
     * @param id 标识
     * @return 删除的结果
     */
    public ReturnResult<Boolean> delete(Long id) {
        if (id == null || projectMapper.selectById(id) == null) {
            return ReturnResult.error("项目不存在: " + id);
        }
        // 级联清理部署日志
        deployLogMapper.delete(new LambdaQueryWrapper<MsProjectDeployLog>()
                .eq(MsProjectDeployLog::getMsProjectDeployLogProjectId, id));
        projectMapper.deleteById(id);
        return ReturnResult.ok(true, "删除成功");
    }

    @Override
    public ReturnResult<MsProject> get(Long id) {
        MsProject project = id == null ? null : projectMapper.selectById(id);
        if (project == null) {
            return ReturnResult.error("项目不存在: " + id);
        }
        return ReturnResult.ok(project);
    }

    @Override
    public ReturnResult<List<MsProject>> query(String keyword, String type) {
        LambdaQueryWrapper<MsProject> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(MsProject::getMsProjectName, keyword)
                    .or().like(MsProject::getMsProjectCode, keyword));
        }
        wrapper.eq(type != null && !type.isBlank(), MsProject::getMsProjectType, type);
        wrapper.orderByDesc(MsProject::getMsProjectSort)
                .orderByDesc(MsProject::getCreateTime);
        return ReturnResult.ok(projectMapper.selectList(wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新已启用。
     * @param id 标识
     * @param enabled 已启用
     * @return 更新已启用的结果
     */
    public ReturnResult<Boolean> updateEnabled(Long id, Boolean enabled) {
        MsProject project = id == null ? null : projectMapper.selectById(id);
        if (project == null) {
            return ReturnResult.error("项目不存在: " + id);
        }
        MsProject update = new MsProject();
        update.setId(id);
        update.setMsProjectEnabled(Boolean.TRUE.equals(enabled) ? 1 : 0);
        projectMapper.updateById(update);
        return ReturnResult.ok(true, Boolean.TRUE.equals(enabled) ? "已启用" : "已停用");
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> hostOptions(String os) {
        LambdaQueryWrapper<MsServerHost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsServerHost::getServerHostStatus, 1);
        wrapper.like(os != null && !os.isBlank(), MsServerHost::getServerHostOsType, os.toUpperCase());
        wrapper.orderByAsc(MsServerHost::getServerHostName);
        List<Map<String, Object>> options = new ArrayList<>();
        for (MsServerHost host : serverHostMapper.selectList(wrapper)) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", host.getId());
            item.put("name", host.getServerHostName());
            item.put("address", host.getServerHostAddress());
            item.put("osType", host.getServerHostOsType());
            options.add(item);
        }
        return ReturnResult.ok(options);
    }

    /**
     * 校验项目参数并回填主机 OS 类型
     *
     * @param project 项目
     * @return 校验失败的错误结果，合法返回 空
     */
    private ReturnResult<MsProject> validate(MsProject project) {
        if (project == null) {
            return ReturnResult.error("项目不能为空");
        }
        if (isBlank(project.getMsProjectName()) || isBlank(project.getMsProjectCode())) {
            return ReturnResult.error("项目名称与编码不能为空");
        }
        if (isBlank(project.getMsProjectDeployDir())) {
            return ReturnResult.error("部署目录不能为空");
        }
        if (isBlank(project.getMsProjectType())) {
            return ReturnResult.error("项目类型不能为空");
        }
        List<Long> hostIds = parseHostIds(project.getMsProjectHostIds());
        if (hostIds.isEmpty()) {
            return ReturnResult.error("至少选择一台目标服务器");
        }
        // 查询主机并校验同 OS 类型
        Set<String> osSet = new LinkedHashSet<>();
        for (Long hostId : hostIds) {
            MsServerHost host = serverHostMapper.selectById(hostId);
            if (host == null) {
                return ReturnResult.error("目标服务器不存在: " + hostId);
            }
            if (host.getServerHostOsType() != null && !host.getServerHostOsType().isBlank()) {
                osSet.add(host.getServerHostOsType().trim().toUpperCase());
            }
        }
        if (osSet.size() > 1) {
            return ReturnResult.error("所选服务器操作系统类型不一致，仅支持同类型(LINUX/WINDOWS)部署");
        }
        project.setMsProjectHostOs(osSet.isEmpty() ? null : osSet.iterator().next());
        return null;
    }

    /**
      * 解析 主机标识 JSON 数组
     *
     * @param json JSON 字符串
     * @return ID 列表，解析失败返回空列表
     */
    private List<Long> parseHostIds(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            log.warn("[project] hostIds 解析失败: {}", json);
            return List.of();
        }
    }

    /**
     * 空串判断
     *
     * @param value 待判断字符串
     * @return 是否为空
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}