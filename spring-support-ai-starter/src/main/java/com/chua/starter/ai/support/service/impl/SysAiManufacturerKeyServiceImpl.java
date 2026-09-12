package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.entity.SysAiManufacturerKey;
import com.chua.starter.ai.support.mapper.SysAiManufacturerKeyMapper;
import com.chua.starter.ai.support.service.SysAiManufacturerKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
* AI 厂商 API 钥匙 服务实现。
*
* <p>基于 MyBatis Plus 操作 {@code sys_ai_manufacturer_key} 表，
* 提供按厂商编码的多 钥匙 管理与启停切换。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiManufacturerKeyServiceImpl implements SysAiManufacturerKeyService {

    /**
    * 厂商 API 钥匙 映射器
     */
    private final SysAiManufacturerKeyMapper manufacturerKeyMapper;

    /**
    * 查询全部厂商 API 钥匙。
    *
    * @return API 钥匙 列表
     */
    @Override
    public List<SysAiManufacturerKey> list() {
        return manufacturerKeyMapper.selectList(null);
    }

    /**
    * 按厂商编码查询 API 钥匙 列表。
    *
    * @param manufacturerCode 厂商编码
    * @return API 钥匙 列表
     */
    @Override
    public List<SysAiManufacturerKey> listByManufacturerCode(String manufacturerCode) {
        LambdaQueryWrapper<SysAiManufacturerKey> wrapper = Wrappers.<SysAiManufacturerKey>lambdaQuery()
                .eq(SysAiManufacturerKey::getSysAiManufacturerCode, manufacturerCode);
        return manufacturerKeyMapper.selectList(wrapper);
    }

    /**
    * 新增厂商 API 钥匙。
    *
    * @param manufacturerKey API 钥匙 实体
    * @return 保存后的实体
     */
    @Override
    public SysAiManufacturerKey save(SysAiManufacturerKey manufacturerKey) {
        manufacturerKey.setStatus(
                manufacturerKey.getStatus() == null ? 1 : manufacturerKey.getStatus());
        manufacturerKey.setWeight(
                manufacturerKey.getWeight() == null ? 0 : manufacturerKey.getWeight());
        manufacturerKey.setCreateTime(LocalDateTime.now());
        manufacturerKey.setUpdateTime(LocalDateTime.now());
        manufacturerKeyMapper.insert(manufacturerKey);
        return manufacturerKey;
    }

    /**
    * 更新厂商 API 钥匙。
    *
    * @param manufacturerKey API 钥匙 实体
    * @return 是否成功
     */
    @Override
    public boolean update(SysAiManufacturerKey manufacturerKey) {
        if (manufacturerKey == null || manufacturerKey.getId() == null) {
            log.warn("[SysAiManufacturerKey] update 失败: ID 为空");
            return false;
        }
        SysAiManufacturerKey existing = manufacturerKeyMapper.selectById(manufacturerKey.getId());
        // 编辑时脱敏占位值或空值不覆盖真实密钥
        if (manufacturerKey.getValue() != null
                && (manufacturerKey.getValue().isBlank()
                || manufacturerKey.getValue().contains("****"))) {
            manufacturerKey.setValue(
                    existing != null ? existing.getValue() : null);
        }
        if (manufacturerKey.getSecret() != null
                && (manufacturerKey.getSecret().isBlank()
                || manufacturerKey.getSecret().contains("****"))) {
            manufacturerKey.setSecret(
                    existing != null ? existing.getSecret() : null);
        }
        manufacturerKey.setUpdateTime(LocalDateTime.now());
        return manufacturerKeyMapper.updateById(manufacturerKey) > 0;
    }

    /**
    * 按 标识 删除厂商 API 钥匙。
    *
    * @param id 主键 标识
    * @return 是否成功
     */
    @Override
    public boolean delete(Long id) {
        if (id == null) {
            return false;
        }
        return manufacturerKeyMapper.deleteById(id) > 0;
    }

    /**
    * 切换 API 钥匙 启停状态。
    *
    * @param id      主键 标识
    * @param enabled 是否启用
    * @return 是否成功
     */
    @Override
    public boolean toggle(Long id, Boolean enabled) {
        SysAiManufacturerKey key = manufacturerKeyMapper.selectById(id);
        if (key == null) {
            log.warn("[SysAiManufacturerKey] toggle 失败: ID {} 不存在", id);
            return false;
        }
        key.setStatus(Boolean.TRUE.equals(enabled) ? 1 : 0);
        key.setUpdateTime(LocalDateTime.now());
        return manufacturerKeyMapper.updateById(key) > 0;
    }
}
