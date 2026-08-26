package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.json.Json;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.mapper.SysAiBotBindingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 微信 iLink Bot 绑定服务。
 *
 * <p>管理多个 Bot 绑定记录的增删改查与在线同步。
 * 每条绑定对应一个独立的微信 Bot 实例。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBotBindingService {

    /** Bot 绑定 Mapper */
    private final SysAiBotBindingMapper botBindingMapper;

    /**
     * 获取全部绑定列表。
     *
     * @return 绑定列表，按创建时间降序
     */
    public List<SysAiBotBinding> listAll() {
        return botBindingMapper.selectList(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .orderByDesc(SysAiBotBinding::getCreateTime));
    }

    /**
     * 按 ID 获取绑定。
     *
     * @param id 绑定 ID
     * @return 绑定信息或 null
     */
    public SysAiBotBinding getById(Long id) {
        return botBindingMapper.selectById(id);
    }

    /**
     * 按 botId 查找绑定。
     *
     * @param botId iLink 分配的唯一标识
     * @return 绑定信息或 null
     */
    public SysAiBotBinding findByBotId(String botId) {
        return botBindingMapper.selectOne(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .eq(SysAiBotBinding::getBotId, botId));
    }

    /**
     * 创建或更新绑定（按 botId 去重）。
     *
     * @param binding 绑定实体
     * @return 数据库 ID
     */
    public Long saveOrUpdate(SysAiBotBinding binding) {
        SysAiBotBinding existing = findByBotId(binding.getBotId());
        if (existing != null) {
            binding.setId(existing.getId());
            binding.setUpdateTime(LocalDateTime.now());
            botBindingMapper.updateById(binding);
            return existing.getId();
        }
        binding.setCreateTime(LocalDateTime.now());
        binding.setUpdateTime(LocalDateTime.now());
        botBindingMapper.insert(binding);
        return binding.getId();
    }

    /**
     * 更新在线状态。
     *
     * @param botId  Bot 唯一标识
     * @param status 新状态（online / offline / expired）
     */
    public void updateStatus(String botId, String status) {
        SysAiBotBinding binding = findByBotId(botId);
        if (binding != null) {
            binding.setStatus(status);
            binding.setLastActiveTime(LocalDateTime.now());
            binding.setUpdateTime(LocalDateTime.now());
            botBindingMapper.updateById(binding);
        }
    }

    /**
     * 删除绑定。
     *
     * @param id 数据库 ID
     */
    public void delete(Long id) {
        botBindingMapper.deleteById(id);
    }
}
