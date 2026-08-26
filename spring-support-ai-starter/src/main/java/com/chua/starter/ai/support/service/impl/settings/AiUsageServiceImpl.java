package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.settings.SysAiSettingUsage;
import com.chua.starter.ai.support.mapper.settings.AiUsageMapper;
import com.chua.starter.ai.support.model.AiUsageDTO;
import com.chua.starter.ai.support.service.settings.AiUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用量统计服务实现类
 *
 * @since 2026-08-03
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageServiceImpl implements AiUsageService {

    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 用量 Mapper
     */
    private final AiUsageMapper usageMapper;

    /**
     * 查询用量，可按模型过滤。
     *
     * @param model 模型名称（可选）
     * @return 用量 DTO 列表
     */
    @Override
    public List<AiUsageDTO> query(String model) {
        LambdaQueryWrapper<SysAiSettingUsage> wrapper = new LambdaQueryWrapper<SysAiSettingUsage>()
                .orderByDesc(SysAiSettingUsage::getUsageTimestamp);
        if (model != null && !model.isBlank()) {
            wrapper.eq(SysAiSettingUsage::getUsageModel, model);
        }
        return usageMapper.selectList(wrapper)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 实体转 DTO。
     *
     * @param entity 用量实体
     * @return 用量 DTO
     */
    private AiUsageDTO toDto(SysAiSettingUsage entity) {
        AiUsageDTO dto = new AiUsageDTO();
        dto.setModel(entity.getUsageModel());
        dto.setRequests(1L);
        dto.setPromptTokens(entity.getUsagePromptTokens());
        dto.setCompletionTokens(entity.getUsageCompletionTokens());
        dto.setTotalTokens(entity.getUsageTotalTokens());
        dto.setTimestamp(entity.getUsageTimestamp() != null ? entity.getUsageTimestamp().format(FORMATTER) : null);
        return dto;
    }
}
