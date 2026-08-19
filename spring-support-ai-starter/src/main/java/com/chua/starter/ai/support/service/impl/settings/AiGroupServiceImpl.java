package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.settings.AiGroup;
import com.chua.starter.ai.support.mapper.settings.AiGroupMapper;
import com.chua.starter.ai.support.model.AiGroupDTO;
import com.chua.starter.ai.support.service.settings.AiGroupService;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.lang.json.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型分组服务实现类
 *
 * @since 2026-08-03
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGroupServiceImpl implements AiGroupService {

    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 分组 Mapper
     */
    private final AiGroupMapper groupMapper;

    /**
     * 查询所有分组。
     *
     * @return 分组 DTO 列表
     */
    @Override
    public List<AiGroupDTO> list() {
        return groupMapper.selectList(
                        new LambdaQueryWrapper<AiGroup>()
                                .orderByAsc(AiGroup::getGroupCreateTime))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 新建分组。
     *
     * @param group 分组 DTO
     * @return 创建后的分组 DTO
     */
    @Override
    @Transactional
    public AiGroupDTO create(AiGroupDTO group) {
        AiGroup entity = toEntity(group);
        LocalDateTime now = LocalDateTime.now();
        entity.setGroupCreateTime(now);
        entity.setGroupUpdateTime(now);
        if (entity.getGroupEnabled() == null) {
            entity.setGroupEnabled(true);
        }
        if (entity.getGroupDeleted() == null) {
            entity.setGroupDeleted(0);
        }
        groupMapper.insert(entity);
        log.debug("[AiGroup] 新建成功: id={}, name={}", entity.getGroupId(), entity.getGroupName());
        return toDto(entity);
    }

    /**
     * 更新分组。
     *
     * @param groupId 分组主键
     * @param group   分组 DTO
     * @return 更新后的分组 DTO
     */
    @Override
    @Transactional
    public AiGroupDTO update(Long groupId, AiGroupDTO group) {
        AiGroup entity = toEntity(group);
        entity.setGroupId(groupId);
        entity.setGroupUpdateTime(LocalDateTime.now());
        groupMapper.updateById(entity);
        return toDto(groupMapper.selectById(groupId));
    }

    /**
     * 删除分组（逻辑删除）。
     *
     * @param groupId 分组主键
     */
    @Override
    @Transactional
    public void delete(Long groupId) {
        groupMapper.deleteById(groupId);
        log.debug("[AiGroup] 删除成功: id={}", groupId);
    }

    /**
     * 实体转 DTO。
     *
     * @param entity 分组实体
     * @return 分组 DTO
     */
    private AiGroupDTO toDto(AiGroup entity) {
        if (entity == null) {
            return null;
        }
        AiGroupDTO dto = new AiGroupDTO();
        dto.setId(entity.getGroupId());
        dto.setName(entity.getGroupName());
        dto.setDescription(entity.getGroupDescription());
        dto.setModels(parseStringList(entity.getGroupModels()));
        dto.setAppKeyIds(parseLongList(entity.getGroupAppKeyIds()));
        dto.setCreatedAt(entity.getGroupCreateTime() != null ? entity.getGroupCreateTime().format(FORMATTER) : null);
        return dto;
    }

    /**
     * DTO 转实体。
     *
     * @param dto 分组 DTO
     * @return 分组实体
     */
    private AiGroup toEntity(AiGroupDTO dto) {
        AiGroup entity = new AiGroup();
        entity.setGroupName(dto.getName());
        entity.setGroupDescription(dto.getDescription());
        entity.setGroupModels(dto.getModels() == null ? "[]" : Json.toJson(dto.getModels()));
        entity.setGroupAppKeyIds(dto.getAppKeyIds() == null ? "[]" : Json.toJson(dto.getAppKeyIds()));
        return entity;
    }

    /**
     * 解析 JSON 字符串列表。
     *
     * @param content JSON 数组字符串
     * @return 字符串列表
     */
    private List<String> parseStringList(String content) {
        return Converter.convertIfListNecessary(content, String.class);
    }

    /**
     * 解析 JSON Long 列表。
     *
     * @param content JSON 数组字符串
     * @return Long 列表
     */
    private List<Long> parseLongList(String content) {
        return Converter.convertIfListNecessary(content, Long.class);
    }
}
