package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.ai.support.entity.settings.AiServiceStatus;
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
     * 服务状态 Mapper
     */
    private final AiServiceStatusMapper statusMapper;

    /**
     * 查询所有服务运行状态，并实时计算运行时长。
     *
     * @return 服务状态 DTO 列表
     */
    @Override
    public List<AiServiceStatusDTO> list() {
        List<AiServiceStatus> statuses = statusMapper.selectList(
                new LambdaQueryWrapper<AiServiceStatus>()
                        .orderByAsc(AiServiceStatus::getStatusId));
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
        AiServiceStatus exist = statusMapper.selectOne(
                new LambdaQueryWrapper<AiServiceStatus>()
                        .eq(AiServiceStatus::getStatusServiceName, serviceName));
        LocalDateTime now = LocalDateTime.now();
        if (exist == null) {
            AiServiceStatus status = new AiServiceStatus();
            status.setStatusServiceName(serviceName);
            status.setStatusRunning(true);
            status.setStatusStartTime(now);
            status.setStatusUptimeSec(0L);
            status.setStatusUpdateTime(now);
            statusMapper.insert(status);
            log.info("[AiServiceStatus] 服务 {} 启动", serviceName);
            return;
        }
        LambdaUpdateWrapper<AiServiceStatus> wrapper = new LambdaUpdateWrapper<AiServiceStatus>()
                .eq(AiServiceStatus::getStatusServiceName, serviceName)
                .set(AiServiceStatus::getStatusRunning, true)
                .set(AiServiceStatus::getStatusStartTime, now)
                .set(AiServiceStatus::getStatusUpdateTime, now);
        statusMapper.update(null, wrapper);
        log.info("[AiServiceStatus] 服务 {} 启动", serviceName);
    }

    /**
     * 停止指定服务。
     *
     * @param serviceName 服务名称
     */
    @Override
    @Transactional
    public void stop(String serviceName) {
        AiServiceStatus exist = statusMapper.selectOne(
                new LambdaQueryWrapper<AiServiceStatus>()
                        .eq(AiServiceStatus::getStatusServiceName, serviceName));
        if (exist == null) {
            log.warn("[AiServiceStatus] 服务 {} 不存在，忽略停止", serviceName);
            return;
        }
        refreshUptime(exist);
        LambdaUpdateWrapper<AiServiceStatus> wrapper = new LambdaUpdateWrapper<AiServiceStatus>()
                .eq(AiServiceStatus::getStatusServiceName, serviceName)
                .set(AiServiceStatus::getStatusRunning, false)
                .set(AiServiceStatus::getStatusUptimeSec, exist.getStatusUptimeSec())
                .set(AiServiceStatus::getStatusUpdateTime, LocalDateTime.now());
        statusMapper.update(null, wrapper);
        log.info("[AiServiceStatus] 服务 {} 停止", serviceName);
    }

    /**
     * 刷新运行时长：按当前时间与启动时间差额计算。
     *
     * @param status 服务状态实体
     * @return 刷新后的服务状态实体
     */
    private AiServiceStatus refreshUptime(AiServiceStatus status) {
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
    private AiServiceStatusDTO toDto(AiServiceStatus entity) {
        AiServiceStatusDTO dto = new AiServiceStatusDTO();
        dto.setName(entity.getStatusServiceName());
        dto.setRunning(entity.getStatusRunning());
        dto.setPid(entity.getStatusPid());
        dto.setUptime(entity.getStatusUptimeSec());
        return dto;
    }
}
