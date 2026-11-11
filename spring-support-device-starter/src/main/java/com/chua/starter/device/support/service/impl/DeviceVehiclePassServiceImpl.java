package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.device.support.entity.DeviceVehiclePass;
import com.chua.starter.device.support.mapper.DeviceVehiclePassMapper;
import com.chua.starter.device.support.service.DeviceVehiclePassService;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车辆过车记录服务实现。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceVehiclePassServiceImpl implements DeviceVehiclePassService {

    /**
     * 过车记录映射器
     */
    private final DeviceVehiclePassMapper passMapper;

    @Override
    public DeviceVehiclePass record(DeviceVehiclePass pass) {
        if (pass == null) {
            throw new IllegalArgumentException("过车记录不能为空");
        }
        if (pass.getDeviceVehiclePassTime() == null) {
            pass.setDeviceVehiclePassTime(LocalDateTime.now());
        }
        if (pass.getDeviceVehiclePassCreatedAt() == null) {
            pass.setDeviceVehiclePassCreatedAt(LocalDateTime.now());
        }
        passMapper.insert(pass);
        log.info("过车记录: gate={}, plate={}, direction={}", pass.getDeviceVehiclePassGateId(),
                pass.getDeviceVehiclePassPlateNo(), pass.getDeviceVehiclePassDirection());
        return pass;
    }

    @Override
    public List<DeviceVehiclePass> listPasses(String gateId, String direction,
                                              LocalDateTime start, LocalDateTime end) {
        return passMapper.selectList(buildQuery(gateId, direction, start, end));
    }

    @Override
    public PageResult<DeviceVehiclePass> pagePasses(int page, int size, String gateId, String direction,
                                                    LocalDateTime start, LocalDateTime end) {
        Page<DeviceVehiclePass> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        return PageResult.copy(passMapper.selectPage(query,
                buildQuery(gateId, direction, start, end)));
    }

    /**
     * 组装查询条件。
     *
     * @param gateId    车闸标识
     * @param direction 方向
     * @param start     起始时间
     * @param end       结束时间
     * @return 查询条件
     */
    private LambdaQueryWrapper<DeviceVehiclePass> buildQuery(String gateId, String direction,
                                                            LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<DeviceVehiclePass> wrapper = new LambdaQueryWrapper<>();
        if (gateId != null && !gateId.isBlank()) {
            wrapper.eq(DeviceVehiclePass::getDeviceVehiclePassGateId, gateId);
        }
        if (direction != null && !direction.isBlank()) {
            wrapper.eq(DeviceVehiclePass::getDeviceVehiclePassDirection, direction);
        }
        if (start != null) {
            wrapper.ge(DeviceVehiclePass::getDeviceVehiclePassTime, start);
        }
        if (end != null) {
            wrapper.le(DeviceVehiclePass::getDeviceVehiclePassTime, end);
        }
        wrapper.orderByDesc(DeviceVehiclePass::getDeviceVehiclePassTime);
        return wrapper;
    }
}
