package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.device.support.entity.DeviceVehicle;
import com.chua.starter.device.support.mapper.DeviceVehicleMapper;
import com.chua.starter.device.support.service.DeviceVehicleService;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车辆档案服务实现。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceVehicleServiceImpl implements DeviceVehicleService {

    /**
     * 车辆映射器
     */
    private final DeviceVehicleMapper vehicleMapper;

    @Override
    public DeviceVehicle addVehicle(DeviceVehicle vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("车辆不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        vehicle.setDeviceVehicleDeleted(false);
        vehicle.setDeviceVehicleCreatedAt(now);
        vehicle.setDeviceVehicleUpdatedAt(now);
        vehicleMapper.insert(vehicle);
        log.info("新增车辆: plateNo={}, vendor={}", vehicle.getDeviceVehiclePlateNo(),
                vehicle.getDeviceVehicleVendor());
        return vehicle;
    }

    @Override
    public void updateVehicle(String id, DeviceVehicle vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("车辆不能为空");
        }
        vehicle.setDeviceVehicleId(id);
        vehicle.setDeviceVehicleUpdatedAt(LocalDateTime.now());
        vehicleMapper.updateById(vehicle);
    }

    @Override
    public void deleteVehicle(String id) {
        DeviceVehicle vehicle = new DeviceVehicle();
        vehicle.setDeviceVehicleId(id);
        vehicle.setDeviceVehicleDeleted(true);
        vehicle.setDeviceVehicleUpdatedAt(LocalDateTime.now());
        vehicleMapper.updateById(vehicle);
        log.info("删除车辆: id={}", id);
    }

    @Override
    public DeviceVehicle getVehicle(String id) {
        return vehicleMapper.selectById(id);
    }

    @Override
    public DeviceVehicle getByPlateNo(String plateNo) {
        if (plateNo == null || plateNo.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<DeviceVehicle> wrapper = activeQuery();
        wrapper.eq(DeviceVehicle::getDeviceVehiclePlateNo, plateNo.trim());
        List<DeviceVehicle> list = vehicleMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<DeviceVehicle> listVehicles(String keyword) {
        return vehicleMapper.selectList(buildQuery(keyword));
    }

    @Override
    public PageResult<DeviceVehicle> pageVehicles(int page, int size, String keyword) {
        Page<DeviceVehicle> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        return PageResult.copy(vehicleMapper.selectPage(query, buildQuery(keyword)));
    }

    /**
     * 未删除条件。
     *
     * @return 查询条件
     */
    private LambdaQueryWrapper<DeviceVehicle> activeQuery() {
        LambdaQueryWrapper<DeviceVehicle> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.isNull(DeviceVehicle::getDeviceVehicleDeleted)
                .or().eq(DeviceVehicle::getDeviceVehicleDeleted, false));
        return wrapper;
    }

    /**
     * 组装查询条件。
     *
     * @param keyword 关键字
     * @return 查询条件
     */
    private LambdaQueryWrapper<DeviceVehicle> buildQuery(String keyword) {
        LambdaQueryWrapper<DeviceVehicle> wrapper = activeQuery();
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(DeviceVehicle::getDeviceVehiclePlateNo, value)
                    .or().like(DeviceVehicle::getDeviceVehicleOwnerName, value)
                    .or().like(DeviceVehicle::getDeviceVehicleOwnerPhone, value));
        }
        wrapper.orderByDesc(DeviceVehicle::getDeviceVehicleUpdatedAt);
        return wrapper;
    }

    @Override
    public DeviceVehicle renewVehicle(String id, Integer months, LocalDateTime validTo) {
        DeviceVehicle existing = vehicleMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("车辆不存在: " + id);
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = existing.getDeviceVehicleValidTo() != null
                && existing.getDeviceVehicleValidTo().isAfter(now)
                ? existing.getDeviceVehicleValidTo() : now;
        LocalDateTime newValidTo;
        if (validTo != null) {
            newValidTo = validTo;
        } else {
            int step = months == null ? 12 : months;
            if (step < 1 || step > 120) {
                throw new IllegalArgumentException("续期月数必须在 1-120 之间");
            }
            newValidTo = base.plusMonths(step);
        }
        if (!newValidTo.isAfter(base)) {
            throw new IllegalArgumentException("新的有效期必须晚于当前有效期");
        }
        DeviceVehicle update = new DeviceVehicle();
        update.setDeviceVehicleId(id);
        update.setDeviceVehicleValidFrom(existing.getDeviceVehicleValidFrom() == null
                ? now : existing.getDeviceVehicleValidFrom());
        update.setDeviceVehicleValidTo(newValidTo);
        update.setDeviceVehicleUpdatedAt(now);
        vehicleMapper.updateById(update);
        log.info("车辆续期: id={}, validTo={}", id, newValidTo);
        return vehicleMapper.selectById(id);
    }
}
