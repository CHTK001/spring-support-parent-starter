package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.entity.SysAiSessionCompression;
import com.chua.starter.ai.support.mapper.SysAiSessionCompressionMapper;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.service.SysAiSessionCompressionService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * AI 会话压缩上下文服务实现。
 *
 * <p>基于 MyBatis Plus 操作 {@code sys_ai_session_compression} 表，
 * 按会话维度保存压缩后的摘要上下文。</p>
 *
 * <p>压缩摘要会整段回灌给模型，属于会话私有数据，故按会话归属（
 * {@code sys_ai_session.sys_user_id}）读写：保存时以会话归属落用户，
 * 读取时非管理员只能取到本人会话的摘要。压缩步骤常在 SSE/线程池里执行，
 * 那里取不到登录身份，因此无登录上下文时只放行不拦截，避免后台任务被误伤。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiSessionCompressionServiceImpl implements SysAiSessionCompressionService {

    /**
     * 压缩上下文 映射器
     */
    private final SysAiSessionCompressionMapper compressionMapper;

    /**
     * 会话 映射器，用于取会话归属用户
     */
    private final SysAiSessionMapper sysAiSessionMapper;

    /**
     * 按会话 标识 查询压缩上下文。
     *
     * @param sessionId 会话 标识
     * @return 压缩上下文实体，不存在时返回 {@code null}
     */
    @Override
    public SysAiSessionCompression getBySessionId(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        SysAiSessionCompression compression = findBySessionId(sessionId);
        if (compression == null || readableByCurrentUser(compression, sessionId)) {
            return compression;
        }
        log.warn("[SysAiSessionCompression] 会话 {} 的压缩上下文归属他人，已忽略", sessionId);
        return null;
    }

    /**
     * 按会话 标识 取压缩上下文。
     *
     * <p>必须走 映射器 基础方法的映射（列名取自实体注解），自定义 {@code SELECT *}
     * 的结果映射不到主键与归属列，会让后续的按主键更新静默更新 0 行。</p>
     *
     * @param sessionId 会话 标识，不能为空
     * @return 压缩上下文实体，不存在时返回 {@code null}
     */
    private SysAiSessionCompression findBySessionId(Long sessionId) {
        return compressionMapper.selectOne(Wrappers.<SysAiSessionCompression>lambdaQuery()
                .eq(SysAiSessionCompression::getSysAiSessionId, sessionId));
    }

    /**
     * 当前调用方可否读取该会话的压缩上下文。
     *
     * @param compression 压缩上下文实体，不允许为 空
     * @param sessionId   会话 标识
     * @return 无登录上下文或归属一致时为 {@code true}
     */
    private boolean readableByCurrentUser(SysAiSessionCompression compression, Long sessionId) {
        String userId = AiUserContext.currentUserId();
        if (userId == null || AiUserContext.canAccessOthersData()) {
            // 后台线程取不到身份（机器流量），按信任放行
            return true;
        }
        String owner = compression.getUserId();
        return owner == null ? Objects.equals(userId, sessionOwner(sessionId)) : userId.equals(owner.trim());
    }

    /**
     * 保存（新增或更新）会话的压缩上下文。
     *
     * @param compression 压缩上下文实体
     */
    @Override
    public void saveOrUpdate(SysAiSessionCompression compression) {
        if (compression == null || compression.getSysAiSessionId() == null) {
            log.warn("[SysAiSessionCompression] 保存失败: 会话 ID 为空");
            return;
        }
        SysAiSessionCompression existing = findBySessionId(compression.getSysAiSessionId());
        LocalDateTime now = LocalDateTime.now();
        String owner = sessionOwner(compression.getSysAiSessionId());
        if (existing != null) {
            existing.setSysAiManufacturerCode(compression.getSysAiManufacturerCode());
            existing.setSysAiModuleCode(compression.getSysAiModuleCode());
            existing.setSysAiCompressionMessageCount(compression.getSysAiCompressionMessageCount());
            existing.setSysAiCompressionFromMessageId(compression.getSysAiCompressionFromMessageId());
            existing.setSysAiCompressionContext(compression.getSysAiCompressionContext());
            // 历史数据没有归属，按会话当前归属补齐；已有归属的不随调用方改变
            if (existing.getUserId() == null || existing.getUserId().isBlank()) {
                existing.setUserId(owner);
            }
            existing.setUpdateTime(now);
            compressionMapper.updateById(existing);
        } else {
            compression.setUserId(owner);
            compression.setCreateTime(now);
            compression.setUpdateTime(now);
            compressionMapper.insert(compression);
        }
    }

    /**
     * 取会话归属用户。
     *
     * @param sessionId 会话 标识，可为空
     * @return 归属用户标识；会话不存在或未归属返回 {@code null}
     */
    private String sessionOwner(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        SysAiSession session = sysAiSessionMapper.selectById(sessionId);
        if (session == null || session.getSysUserId() == null || session.getSysUserId().isBlank()) {
            return null;
        }
        return session.getSysUserId().trim();
    }

    /**
     * 删除会话的压缩上下文。
     *
     * @param sessionId 会话 标识
     */
    @Override
    public void deleteBySessionId(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        String userId = AiUserContext.currentUserId();
        if (userId != null && !userId.equals(sessionOwner(sessionId))) {
            // 写出口只认归属，管理员同样不能删他人会话的上下文
            log.warn("[SysAiSessionCompression] 用户 {} 无权删除会话 {} 的压缩上下文", userId, sessionId);
            return;
        }
        LambdaQueryWrapper<SysAiSessionCompression> wrapper = Wrappers.<SysAiSessionCompression>lambdaQuery()
                .eq(SysAiSessionCompression::getSysAiSessionId, sessionId);
        compressionMapper.delete(wrapper);
    }
}
