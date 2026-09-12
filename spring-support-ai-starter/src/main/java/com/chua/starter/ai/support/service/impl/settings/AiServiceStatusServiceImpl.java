package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.ai.support.entity.settings.SysAiServiceStatus;
import com.chua.starter.ai.support.mapper.settings.AiServiceStatusMapper;
import com.chua.starter.ai.support.model.AiServiceStatusDTO;
import com.chua.starter.ai.support.service.settings.AiServiceStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
* 聚合服务运行状态服务实现类
*
* @since 2026-08-03
* @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceStatusServiceImpl implements AiServiceStatusService {

    /**
    * 服务状态 映射器
     */
    private final AiServiceStatusMapper statusMapper;

    /**
    * 查询所有服务运行状态，并实时计算运行时长。
    *
    * @return 服务状态 DTO 列表
     */
    @Override
    public List<AiServiceStatusDTO> list() {
        List<SysAiServiceStatus> statuses = statusMapper.selectList(
                new LambdaQueryWrapper<SysAiServiceStatus>()
                        .orderByAsc(SysAiServiceStatus::getStatusId));
        return statuses.stream()
                .map(status -> toDto(refreshUptime(status)))
                .collect(Collectors.toList());
    }

    /**
    * 启动指定服务，不存在则新建记录。
    *
    * @param serviceName 服务名称
     */
    @Override
    @Transactional
    public void start(String serviceName) {
        SysAiServiceStatus exist = statusMapper.selectOne(
                new LambdaQueryWrapper<SysAiServiceStatus>()
                        .eq(SysAiServiceStatus::getStatusServiceName, serviceName));
        LocalDateTime now = LocalDateTime.now();
        if (exist == null) {
            SysAiServiceStatus status = new SysAiServiceStatus();
            status.setStatusServiceName(serviceName);
            status.setStatusRunning(true);
            status.setStatusStartTime(now);
            status.setStatusUptimeSec(0L);
            status.setStatusUpdateTime(now);
            statusMapper.insert(status);
            log.info("[SysAiServiceStatus] 服务 {} 启动", serviceName);
            return;
        }
        LambdaUpdateWrapper<SysAiServiceStatus> wrapper = new LambdaUpdateWrapper<SysAiServiceStatus>()
                .eq(SysAiServiceStatus::getStatusServiceName, serviceName)
                .set(SysAiServiceStatus::getStatusRunning, true)
                .set(SysAiServiceStatus::getStatusStartTime, now)
                .set(SysAiServiceStatus::getStatusUpdateTime, now);
        statusMapper.update(null, wrapper);
        log.info("[SysAiServiceStatus] 服务 {} 启动", serviceName);
    }

    /**
    * 停止指定服务。
    *
    * @param serviceName 服务名称
     */
    @Override
    @Transactional
    public void stop(String serviceName) {
        SysAiServiceStatus exist = statusMapper.selectOne(
                new LambdaQueryWrapper<SysAiServiceStatus>()
                        .eq(SysAiServiceStatus::getStatusServiceName, serviceName));
        if (exist == null) {
            log.warn("[SysAiServiceStatus] 服务 {} 不存在，忽略停止", serviceName);
            return;
        }
        /**
        * refreshuptime。
        * @param exist exist
         */
        refreshUptime(exist);
        LambdaUpdateWrapper<SysAiServiceStatus> wrapper = new LambdaUpdateWrapper<SysAiServiceStatus>()
                .eq(SysAiServiceStatus::getStatusServiceName, serviceName)
                .set(SysAiServiceStatus::getStatusRunning, false)
                .set(SysAiServiceStatus::getStatusUptimeSec, exist.getStatusUptimeSec())
                .set(SysAiServiceStatus::getStatusUpdateTime, LocalDateTime.now());
        statusMapper.update(null, wrapper);
        log.info("[SysAiServiceStatus] 服务 {} 停止", serviceName);
    }

    /**
    * 刷新运行时长：按当前时间与启动时间差额计算。
    *
    * @param status 服务状态实体
    * @return 刷新后的服务状态实体
     */
    private SysAiServiceStatus refreshUptime(SysAiServiceStatus status) {
        if (status.getStatusRunning() != null && status.getStatusRunning()
                && status.getStatusStartTime() != null) {
            long sec = ChronoUnit.SECONDS.between(status.getStatusStartTime(), LocalDateTime.now());
            status.setStatusUptimeSec(Math.max(0L, sec));
        }
        return status;
    }

    /**
    * 实体转 DTO。
    *
    * @param entity 服务状态实体
    * @return 服务状态 DTO
     */
    private AiServiceStatusDTO toDto(SysAiServiceStatus entity) {
        AiServiceStatusDTO dto = new AiServiceStatusDTO();
        dto.setName(entity.getStatusServiceName());
        dto.setRunning(entity.getStatusRunning());
        dto.setPid(entity.getStatusPid());
        dto.setUptime(entity.getStatusUptimeSec());
        return dto;
    }
}
