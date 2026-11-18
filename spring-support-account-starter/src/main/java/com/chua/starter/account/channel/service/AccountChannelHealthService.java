package com.chua.starter.account.channel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.account.channel.mapper.ChannelHealthMapper;
import com.chua.starter.account.channel.store.ChannelHealth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 渠道健康记录业务（账号模块唯一实现）。
 *
 * <p>健康记录是渠道资产的附属数据（表 {@code sys_account_channel_health}，以
 * 「渠道 + 连接」为唯一键）。<b>状态机不在这里</b>：什么时候算失败、连续几次失败下线、
 * 命中限流怎么记，属于探针（AI 侧）的业务；本类只负责「记录怎么读、怎么落库」，
 * 并提供统一的 upsert 口径，避免消费方各自拼 select + insert/update。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Service
@RequiredArgsConstructor
public class AccountChannelHealthService {

    /**
     * 健康记录表
     */
    private final ChannelHealthMapper healthMapper;

    /**
     * 全部健康记录（按渠道、连接升序）。
     *
     * @return 健康记录列表
     */
    public List<ChannelHealth> listAll() {
        return healthMapper.selectList(new LambdaQueryWrapper<ChannelHealth>()
                .orderByAsc(ChannelHealth::getProviderId)
                .orderByAsc(ChannelHealth::getConnectionId));
    }

    /**
     * 指定渠道下的健康记录。
     *
     * @param providerId 渠道标识
     * @return 健康记录列表
     */
    public List<ChannelHealth> listByProvider(String providerId) {
        return healthMapper.selectList(new LambdaQueryWrapper<ChannelHealth>()
                .eq(ChannelHealth::getProviderId, providerId));
    }

    /**
     * 按连接标识取健康记录。
     *
     * @param connectionId 连接标识
     * @return 健康记录；未探测过返回 {@code null}
     */
    public ChannelHealth find(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return null;
        }
        return healthMapper.selectById(connectionId);
    }

    /**
     * 已下线的连接标识集合（路由层据此剔除）。
     *
     * @param state 目标状态，如 {@code DOWN}
     * @return 连接标识集合
     */
    public Set<String> connectionIdsByState(String state) {
        Set<String> ids = new LinkedHashSet<>();
        if (state == null || state.isBlank()) {
            return ids;
        }
        for (ChannelHealth h : healthMapper.selectList(new LambdaQueryWrapper<ChannelHealth>()
                .eq(ChannelHealth::getState, state))) {
            if (h.getConnectionId() != null) {
                ids.add(h.getConnectionId());
            }
        }
        return ids;
    }

    /**
     * 保存健康记录（不存在则插入，存在则整行更新）。
     *
     * @param h 健康记录行，{@code id} 必填
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int save(ChannelHealth h) {
        if (h == null || h.getId() == null) {
            return 0;
        }
        Long exists = healthMapper.selectCount(new LambdaQueryWrapper<ChannelHealth>()
                .eq(ChannelHealth::getId, h.getId()));
        if (exists == null || exists == 0L) {
            return healthMapper.insert(h);
        }
        return healthMapper.updateById(h);
    }
}
