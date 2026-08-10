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
 * AI 厂商 API Key 服务实现。
 *
 * <p>基于 MyBatis Plus 操作 {@code sys_ai_manufacturer_key} 表，
 * 提供按厂商编码的多 Key 管理与启停切换。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiManufacturerKeyServiceImpl implements SysAiManufacturerKeyService {

    /**
     * 厂商 API Key Mapper
     */
    private final SysAiManufacturerKeyMapper manufacturerKeyMapper;

    /**
     * 查询全部厂商 API Key。
     *
     * @return API Key 列表
     */
    @Override
    public List<SysAiManufacturerKey> list() {
        return manufacturerKeyMapper.selectList(null);
    }

    /**
     * 按厂商编码查询 API Key 列表。
     *
     * @param manufacturerCode 厂商编码
     * @return API Key 列表
     */
    @Override
    public List<SysAiManufacturerKey> listByManufacturerCode(String manufacturerCode) {
        LambdaQueryWrapper<SysAiManufacturerKey> wrapper = Wrappers.<SysAiManufacturerKey>lambdaQuery()
                .eq(SysAiManufacturerKey::getSysAiManufacturerCode, manufacturerCode);
        return manufacturerKeyMapper.selectList(wrapper);
    }

    /**
     * 新增厂商 API Key。
     *
     * @param manufacturerKey API Key 实体
     * @return 保存后的实体
     */
    @Override
    public SysAiManufacturerKey save(SysAiManufacturerKey manufacturerKey) {
        manufacturerKey.setSysAiManufacturerKeyStatus(
                manufacturerKey.getSysAiManufacturerKeyStatus() == null ? 1 : manufacturerKey.getSysAiManufacturerKeyStatus());
        manufacturerKey.setSysAiManufacturerKeyWeight(
                manufacturerKey.getSysAiManufacturerKeyWeight() == null ? 0 : manufacturerKey.getSysAiManufacturerKeyWeight());
        manufacturerKey.setCreateTime(LocalDateTime.now());
        manufacturerKey.setUpdateTime(LocalDateTime.now());
        manufacturerKeyMapper.insert(manufacturerKey);
        return manufacturerKey;
    }

    /**
     * 更新厂商 API Key。
     *
     * @param manufacturerKey API Key 实体
     * @return 是否成功
     */
    @Override
    public boolean update(SysAiManufacturerKey manufacturerKey) {
        if (manufacturerKey == null || manufacturerKey.getSysAiManufacturerKeyId() == null) {
            log.warn("[SysAiManufacturerKey] update 失败: ID 为空");
            return false;
        }
        SysAiManufacturerKey existing = manufacturerKeyMapper.selectById(manufacturerKey.getSysAiManufacturerKeyId());
        // 编辑时脱敏占位值或空值不覆盖真实密钥
        if (manufacturerKey.getSysAiManufacturerKeyValue() != null
                && (manufacturerKey.getSysAiManufacturerKeyValue().isBlank()
                || manufacturerKey.getSysAiManufacturerKeyValue().contains("****"))) {
            manufacturerKey.setSysAiManufacturerKeyValue(
                    existing != null ? existing.getSysAiManufacturerKeyValue() : null);
        }
        if (manufacturerKey.getSysAiManufacturerKeySecret() != null
                && (manufacturerKey.getSysAiManufacturerKeySecret().isBlank()
                || manufacturerKey.getSysAiManufacturerKeySecret().contains("****"))) {
            manufacturerKey.setSysAiManufacturerKeySecret(
                    existing != null ? existing.getSysAiManufacturerKeySecret() : null);
        }
        manufacturerKey.setUpdateTime(LocalDateTime.now());
        return manufacturerKeyMapper.updateById(manufacturerKey) > 0;
    }

    /**
     * 按 ID 删除厂商 API Key。
     *
     * @param id 主键 ID
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
     * 切换 API Key 启停状态。
     *
     * @param id      主键 ID
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
        key.setSysAiManufacturerKeyStatus(Boolean.TRUE.equals(enabled) ? 1 : 0);
        key.setUpdateTime(LocalDateTime.now());
        return manufacturerKeyMapper.updateById(key) > 0;
    }
}
