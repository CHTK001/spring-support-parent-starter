package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.device.support.entity.DevicePerson;
import com.chua.starter.device.support.mapper.DevicePersonMapper;
import com.chua.starter.device.support.service.DevicePersonService;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 厂商人员台账服务实现。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePersonServiceImpl implements DevicePersonService {

    /**
     * 人员映射器
     */
    private final DevicePersonMapper personMapper;

    @Override
    public DevicePerson addPerson(DevicePerson person) {
        if (person == null) {
            throw new IllegalArgumentException("人员不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        person.setDevicePersonDeleted(false);
        person.setDevicePersonCreatedAt(now);
        person.setDevicePersonUpdatedAt(now);
        personMapper.insert(person);
        log.info("新增人员: vendor={}, externalId={}", person.getDevicePersonVendor(),
                person.getDevicePersonExternalId());
        return person;
    }

    @Override
    public void updatePerson(String id, DevicePerson person) {
        if (person == null) {
            throw new IllegalArgumentException("人员不能为空");
        }
        person.setDevicePersonId(id);
        person.setDevicePersonUpdatedAt(LocalDateTime.now());
        personMapper.updateById(person);
    }

    @Override
    public void deletePerson(String id) {
        DevicePerson person = new DevicePerson();
        person.setDevicePersonId(id);
        person.setDevicePersonDeleted(true);
        person.setDevicePersonUpdatedAt(LocalDateTime.now());
        personMapper.updateById(person);
        log.info("删除人员: id={}", id);
    }

    @Override
    public DevicePerson getPerson(String id) {
        return personMapper.selectById(id);
    }

    @Override
    public List<DevicePerson> listPersons(String vendor, String keyword) {
        return personMapper.selectList(buildQuery(vendor, keyword));
    }

    @Override
    public PageResult<DevicePerson> pagePersons(int page, int size, String vendor, String keyword) {
        Page<DevicePerson> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        return PageResult.copy(personMapper.selectPage(query, buildQuery(vendor, keyword)));
    }

    /**
     * 组装查询条件：排除已删除，按厂商与关键字过滤。
     *
     * @param vendor  厂商标识
     * @param keyword 关键字
     * @return 查询条件
     */
    private LambdaQueryWrapper<DevicePerson> buildQuery(String vendor, String keyword) {
        LambdaQueryWrapper<DevicePerson> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.isNull(DevicePerson::getDevicePersonDeleted)
                .or().eq(DevicePerson::getDevicePersonDeleted, false));
        if (vendor != null && !vendor.isBlank()) {
            wrapper.eq(DevicePerson::getDevicePersonVendor, vendor);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(DevicePerson::getDevicePersonName, value)
                    .or().like(DevicePerson::getDevicePersonPhone, value)
                    .or().like(DevicePerson::getDevicePersonExternalId, value));
        }
        wrapper.orderByDesc(DevicePerson::getDevicePersonUpdatedAt);
        return wrapper;
    }
}
