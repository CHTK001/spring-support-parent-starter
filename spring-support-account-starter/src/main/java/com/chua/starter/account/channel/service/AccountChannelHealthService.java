package com.chua.starter.account.channel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.account.channel.mapper.ChannelHealthLogMapper;
import com.chua.starter.account.channel.mapper.ChannelHealthMapper;
import com.chua.starter.account.channel.store.ChannelHealth;
import com.chua.starter.account.channel.store.ChannelHealthLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
     * 健康历史表（追加式时序行）
     */
    private final ChannelHealthLogMapper healthLogMapper;

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
     * 保存健康记录（不存在则插入，存在则整行更新），并同事务追加一行历史。
     *
     * <p>历史追加挂在同一个落库漏斗上：探针结论、人工熔断/放行等任何状态
     * 变更都会留痕，供渠道页「健康」页签画趋势；调用方（AI 侧探针）无需
     * 感知历史表的存在。</p>
     *
     * @param h 健康记录行，{@code id} 必填
     * @return 影响行数（最新态 upsert 的结果）
     */
    @Transactional(rollbackFor = Exception.class)
    public int save(ChannelHealth h) {
        if (h == null || h.getId() == null) {
            return 0;
        }
        Long exists = healthMapper.selectCount(new LambdaQueryWrapper<ChannelHealth>()
                .eq(ChannelHealth::getId, h.getId()));
        int rows;
        if (exists == null || exists == 0L) {
            rows = healthMapper.insert(h);
        } else {
            rows = healthMapper.updateById(h);
        }
        appendHistory(h);
        return rows;
    }

    /**
     * 追加一行健康历史（随 {@link #save} 同事务）。
     *
     * <p>取最新态行里的本次探针遥测（状态 / 延迟 / HTTP 码 / 错误）落一行
     * 只增不改的时序记录；错误摘要截断到 255 字（历史列更短，最新态列保留
     * 512 字完整值）。</p>
     *
     * @param h 刚落库的最新态健康记录
     */
    private void appendHistory(ChannelHealth h) {
        ChannelHealthLog log = new ChannelHealthLog();
        log.setProviderId(h.getProviderId());
        log.setConnectionId(h.getConnectionId());
        log.setChannel(h.getChannel());
        log.setState(h.getState() == null ? "UNKNOWN" : h.getState());
        log.setLatencyMs(h.getLastProbeLatency());
        log.setHttpStatus(h.getLastProbeStatus());
        String error = h.getLastProbeError();
        log.setError(error == null ? null
                : (error.length() > 255 ? error.substring(0, 255) : error));
        log.setCreatedAt(System.currentTimeMillis());
        healthLogMapper.insert(log);
    }

    /**
     * 指定连接最近一段时间的健康历史（按时间升序）。
     *
     * @param connectionId 连接标识
     * @param hours        时间窗（小时），自动收敛到 [1, 168]
     * @return 历史行列表（最多 3000 行，超出按时间窗内截断）
     */
    public List<ChannelHealthLog> listHistory(String connectionId, int hours) {
        if (connectionId == null || connectionId.isBlank()) {
            return new ArrayList<>();
        }
        long windowHours = Math.max(1, Math.min(hours, 24 * 7));
        long since = System.currentTimeMillis() - windowHours * 3600_000L;
        return healthLogMapper.selectList(new LambdaQueryWrapper<ChannelHealthLog>()
                .eq(ChannelHealthLog::getConnectionId, connectionId)
                .ge(ChannelHealthLog::getCreatedAt, since)
                .orderByAsc(ChannelHealthLog::getCreatedAt)
                .last("LIMIT 3000"));
    }
}
