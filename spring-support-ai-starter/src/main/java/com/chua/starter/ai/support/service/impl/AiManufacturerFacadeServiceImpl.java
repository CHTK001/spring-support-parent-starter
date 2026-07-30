package com.chua.starter.ai.support.service.impl;

import com.chua.starter.ai.support.entity.SysAiManufacturer;
import com.chua.starter.ai.support.mapper.SysAiManufacturerMapper;
import com.chua.starter.ai.support.service.AiManufacturerFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 * AI 模型厂商管理服务实现。
 * <p>
 * 基于 MyBatis Plus 操作 sys_ai_manufacturer 表，
 * 提供厂商的增删改查，并缓存列表以降低频繁读取。
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiManufacturerFacadeServiceImpl implements AiManufacturerFacadeService {

    private final SysAiManufacturerMapper sysAiManufacturerMapper;

    @Override
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'sys:ai:manufacturer:list'", unless = "#result == null || #result.isEmpty()")
    public List<SysAiManufacturer> list() {
        return sysAiManufacturerMapper.selectList(null);
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'sys:ai:manufacturer:list'")
    public SysAiManufacturer save(SysAiManufacturer manufacturer) {
        manufacturer.setSysAiManufacturerStatus(
                manufacturer.getSysAiManufacturerStatus() == null ? 1 : manufacturer.getSysAiManufacturerStatus());
        manufacturer.setSysAiManufacturerSort(
                manufacturer.getSysAiManufacturerSort() == null ? 0 : manufacturer.getSysAiManufacturerSort());
        manufacturer.setCreateTime(LocalDateTime.now());
        manufacturer.setUpdateTime(LocalDateTime.now());
        sysAiManufacturerMapper.insert(manufacturer);
        return manufacturer;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'sys:ai:manufacturer:list'")
    public boolean update(SysAiManufacturer manufacturer) {
        if (manufacturer == null || manufacturer.getSysAiManufacturerId() == null) {
            log.warn("[AiManufacturerFacade] update 失败: ID 为空");
            return false;
        }
        manufacturer.setUpdateTime(LocalDateTime.now());
        return sysAiManufacturerMapper.updateById(manufacturer) > 0;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'sys:ai:manufacturer:list'")
    public boolean delete(Integer id) {
        return deleteById(id);
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS,
            key = "'sys:ai:manufacturer:list'")
    public boolean deleteById(Integer id) {
        if (id == null) {
            return false;
        }
        boolean result = sysAiManufacturerMapper.deleteById(id) > 0;
        if (result) {
            log.info("[AiManufacturerFacade] 删除厂商: id={}", id);
        }
        return result;
    }
}
