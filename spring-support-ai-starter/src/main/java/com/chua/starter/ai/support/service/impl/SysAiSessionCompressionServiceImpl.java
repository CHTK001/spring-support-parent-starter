package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.entity.SysAiSessionCompression;
import com.chua.starter.ai.support.mapper.SysAiSessionCompressionMapper;
import com.chua.starter.ai.support.service.SysAiSessionCompressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AI 会话压缩上下文服务实现。
 *
 * <p>基于 MyBatis Plus 操作 {@code sys_ai_session_compression} 表，
 * 按会话维度保存压缩后的摘要上下文。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiSessionCompressionServiceImpl implements SysAiSessionCompressionService {

    /**
     * 压缩上下文 Mapper
     */
    private final SysAiSessionCompressionMapper compressionMapper;

    /**
     * 按会话 ID 查询压缩上下文。
     *
     * @param sessionId 会话 ID
     * @return 压缩上下文实体，不存在时返回 {@code null}
     */
    @Override
    public SysAiSessionCompression getBySessionId(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        return compressionMapper.selectBySessionId(sessionId);
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
        SysAiSessionCompression existing = compressionMapper.selectBySessionId(compression.getSysAiSessionId());
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setSysAiManufacturerCode(compression.getSysAiManufacturerCode());
            existing.setSysAiModuleCode(compression.getSysAiModuleCode());
            existing.setSysAiCompressionMessageCount(compression.getSysAiCompressionMessageCount());
            existing.setSysAiCompressionFromMessageId(compression.getSysAiCompressionFromMessageId());
            existing.setSysAiCompressionContext(compression.getSysAiCompressionContext());
            existing.setUpdateTime(now);
            compressionMapper.updateById(existing);
        } else {
            compression.setCreateTime(now);
            compression.setUpdateTime(now);
            compressionMapper.insert(compression);
        }
    }

    /**
     * 删除会话的压缩上下文。
     *
     * @param sessionId 会话 ID
     */
    @Override
    public void deleteBySessionId(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        LambdaQueryWrapper<SysAiSessionCompression> wrapper = Wrappers.<SysAiSessionCompression>lambdaQuery()
                .eq(SysAiSessionCompression::getSysAiSessionId, sessionId);
        compressionMapper.delete(wrapper);
    }
}
