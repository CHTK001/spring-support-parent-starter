package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.device.support.entity.DeviceBarrierGate;
import com.chua.starter.device.support.mapper.DeviceBarrierGateMapper;
import com.chua.starter.device.support.service.DeviceBarrierGateService;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车闸点位服务实现。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceBarrierGateServiceImpl implements DeviceBarrierGateService {

    /**
     * 车闸映射器
     */
    private final DeviceBarrierGateMapper gateMapper;

    @Override
    public DeviceBarrierGate addGate(DeviceBarrierGate gate) {
        if (gate == null) {
            throw new IllegalArgumentException("车闸不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        gate.setDeviceBarrierGateDeleted(false);
        gate.setDeviceBarrierGateCreatedAt(now);
        gate.setDeviceBarrierGateUpdatedAt(now);
        gateMapper.insert(gate);
        log.info("新增车闸: name={}, direction={}", gate.getDeviceBarrierGateName(),
                gate.getDeviceBarrierGateDirection());
        return gate;
    }

    @Override
    public void updateGate(String id, DeviceBarrierGate gate) {
        if (gate == null) {
            throw new IllegalArgumentException("车闸不能为空");
        }
        gate.setDeviceBarrierGateId(id);
        gate.setDeviceBarrierGateUpdatedAt(LocalDateTime.now());
        gateMapper.updateById(gate);
    }

    @Override
    public void deleteGate(String id) {
        DeviceBarrierGate gate = new DeviceBarrierGate();
        gate.setDeviceBarrierGateId(id);
        gate.setDeviceBarrierGateDeleted(true);
        gate.setDeviceBarrierGateUpdatedAt(LocalDateTime.now());
        gateMapper.updateById(gate);
        log.info("删除车闸: id={}", id);
    }

    @Override
    public DeviceBarrierGate getGate(String id) {
        return gateMapper.selectById(id);
    }

    @Override
    public List<DeviceBarrierGate> listGates(String deviceId, String direction) {
        return gateMapper.selectList(buildQuery(deviceId, direction));
    }

    @Override
    public PageResult<DeviceBarrierGate> pageGates(int page, int size, String deviceId, String direction) {
        Page<DeviceBarrierGate> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        return PageResult.copy(gateMapper.selectPage(query, buildQuery(deviceId, direction)));
    }

    /**
     * 组装查询条件。
     *
     * @param deviceId  设备标识
     * @param direction 方向
     * @return 查询条件
     */
    private LambdaQueryWrapper<DeviceBarrierGate> buildQuery(String deviceId, String direction) {
        LambdaQueryWrapper<DeviceBarrierGate> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.isNull(DeviceBarrierGate::getDeviceBarrierGateDeleted)
                .or().eq(DeviceBarrierGate::getDeviceBarrierGateDeleted, false));
        if (deviceId != null && !deviceId.isBlank()) {
            wrapper.eq(DeviceBarrierGate::getDeviceBarrierGateDeviceId, deviceId);
        }
        if (direction != null && !direction.isBlank()) {
            wrapper.eq(DeviceBarrierGate::getDeviceBarrierGateDirection, direction);
        }
        wrapper.orderByAsc(DeviceBarrierGate::getDeviceBarrierGateName);
        return wrapper;
    }
}
