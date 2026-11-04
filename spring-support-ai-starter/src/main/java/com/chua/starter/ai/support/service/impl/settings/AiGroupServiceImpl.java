package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import com.chua.starter.ai.support.entity.settings.SysAiGroup;
import com.chua.starter.ai.support.mapper.SysAiAppKeyMapper;
import com.chua.starter.ai.support.mapper.settings.AiGroupMapper;
import com.chua.starter.ai.support.model.AiGroupDTO;
import com.chua.starter.ai.support.service.settings.AiGroupService;
import com.chua.common.support.lang.json.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模型分组服务实现类
 *
 * <p><b>「分组成员」的唯一真源是 appKey 侧</b>：归属关系只有一处落地，
 * 即 {@code sys_ai_app_key.sys_ai_app_key_group_id}。{@code sys_ai_group.sys_ai_group_app_key_ids}
 * 只是同一份关系的冗余快照 —— 本类每次写分组都会把它按真源回填，读分组时也以真源为准。</p>
 *
 * <p>为什么必须双向对齐：门禁（{@code AppKeyModelScope}）读的是 Key 侧的
 * {@code group_id}，而分组管理页展示/编辑的是 {@code appKeyIds}。两边不同步时会出现
 * 「分组页里看到 Key 在里面，实际请求却按没绑定放行」这类看不出来的口径分裂。</p>
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
     * 分组 映射器
     */
    private final AiGroupMapper groupMapper;

    /**
     * appKey 映射器——分组成员关系的真源表
     */
    private final SysAiAppKeyMapper appKeyMapper;

    /**
     * 查询所有分组。
     *
     * <p>{@code appKeyIds} 一次性按分组聚合查出，不在循环里逐组查（避免 N+1）。</p>
     *
     * @return 分组 DTO 列表
     */
    @Override
    public List<AiGroupDTO> list() {
        Map<Integer, List<Long>> keysByGroup = appKeyIdsByGroup();
        return groupMapper.selectList(
                        new LambdaQueryWrapper<SysAiGroup>()
                                .orderByAsc(SysAiGroup::getGroupCreateTime))
                .stream()
                .map(entity -> toDto(entity, keysByGroup.get(intGroupId(entity.getGroupId()))))
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
        SysAiGroup entity = toEntity(group);
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
        // 建组时就把成员落到 appKey 侧，再把真源回填进 JSON 快照列
        if (group.getAppKeyIds() != null) {
            syncAppKeys(entity.getGroupId(), group.getAppKeyIds());
            entity.setGroupAppKeyIds(Json.toJson(appKeyIdsOf(entity.getGroupId())));
            groupMapper.updateById(entity);
        }
        log.debug("[SysAiGroup] 新建成功: id={}, name={}", entity.getGroupId(), entity.getGroupName());
        return toDto(entity, appKeyIdsOf(entity.getGroupId()));
    }

    /**
     * 更新分组。
     *
     * <p>{@code appKeyIds} 的三态语义：不传（null）＝ 不动成员；传空数组 ＝ 解绑全部；
     * 传列表 ＝ 最终名单（名单内新增的收进来、名单外原本属于本组的放回「不分组」）。</p>
     *
     * @param groupId 分组主键
     * @param group   分组 DTO
     * @return 更新后的分组 DTO
     */
    @Override
    @Transactional
    public AiGroupDTO update(Long groupId, AiGroupDTO group) {
        SysAiGroup entity = toEntity(group);
        entity.setGroupId(groupId);
        entity.setGroupUpdateTime(LocalDateTime.now());
        if (group.getAppKeyIds() != null) {
            syncAppKeys(groupId, group.getAppKeyIds());
            // 快照列按真源回填：调用方漏传/多传的 id 不会在库里留下不一致的记录
            entity.setGroupAppKeyIds(Json.toJson(appKeyIdsOf(groupId)));
        }
        groupMapper.updateById(entity);
        return toDto(groupMapper.selectById(groupId), appKeyIdsOf(groupId));
    }

    /**
     * 删除分组（逻辑删除）。
     *
     * <p>删之前先把成员放回「不分组」：门禁口径是「分组不存在 = 一个模型都不给」
     * （见 {@code AiChatFacadeServiceImpl#groupModelScope}），不释放的话这些 Key
     * 会在分组删掉之后全线 403，而面板上看不出原因。</p>
     *
     * @param groupId 分组主键
     */
    @Override
    @Transactional
    public void delete(Long groupId) {
        syncAppKeys(groupId, Collections.emptyList());
        groupMapper.deleteById(groupId);
        log.debug("[SysAiGroup] 删除成功: id={}", groupId);
    }

    /**
     * 把「本组成员名单」同步到 appKey 侧（真源）。
     *
     * <p>单列 {@code sys_ai_app_key_group_id} 决定归属，所以一次绑定必然是「先收进来
     * 再放出去」的移动语义：<b>只改本次涉及的行</b>，名单内已有成员不动，避免无谓写库
     * 与 update_time 抖动。</p>
     *
     * @param groupId       分组主键
     * @param desiredKeyIds 期望的成员 Key 主键列表；空集合表示解绑全部
     */
    private void syncAppKeys(Long groupId, List<Long> desiredKeyIds) {
        Integer gid = intGroupId(groupId);
        if (gid == null) {
            return;
        }
        Set<Integer> current = appKeyIdsOf(groupId).stream()
                .filter(Objects::nonNull)
                .map(Long::intValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> desired = new LinkedHashSet<>();
        if (desiredKeyIds != null) {
            for (Long id : desiredKeyIds) {
                if (id != null) {
                    desired.add(id.intValue());
                }
            }
        }
        List<Integer> release = current.stream().filter(id -> !desired.contains(id)).toList();
        if (!release.isEmpty()) {
            appKeyMapper.update(null, new LambdaUpdateWrapper<SysAiAppKey>()
                    .in(SysAiAppKey::getId, release)
                    .eq(SysAiAppKey::getGroupId, gid)
                    .set(SysAiAppKey::getGroupId, null));
            log.debug("[SysAiGroup] 解绑 appKey: groupId={}, keys={}", gid, release);
        }
        List<Integer> bind = desired.stream().filter(id -> !current.contains(id)).toList();
        if (!bind.isEmpty()) {
            appKeyMapper.update(null, new LambdaUpdateWrapper<SysAiAppKey>()
                    .in(SysAiAppKey::getId, bind)
                    .set(SysAiAppKey::getGroupId, gid));
            log.debug("[SysAiGroup] 绑定 appKey: groupId={}, keys={}", gid, bind);
        }
    }

    /**
     * 读取分组当前的成员 Key 主键列表（真源）。
     *
     * @param groupId 分组主键
     * @return Key 主键列表，永不为 null
     */
    private List<Long> appKeyIdsOf(Long groupId) {
        Integer gid = intGroupId(groupId);
        if (gid == null) {
            return new ArrayList<>();
        }
        return appKeyMapper.selectByGroupId(gid).stream()
                .map(SysAiAppKey::getId)
                .filter(Objects::nonNull)
                .map(Integer::longValue)
                .collect(Collectors.toList());
    }

    /**
     * 一次性查出「分组 → 成员 Key 主键」的聚合表（列表页用，避免 N+1）。
     *
     * @return 分组主键到 Key 列表的映射
     */
    private Map<Integer, List<Long>> appKeyIdsByGroup() {
        return appKeyMapper.selectList(new LambdaQueryWrapper<SysAiAppKey>()
                        .select(SysAiAppKey::getId, SysAiAppKey::getGroupId)
                        .isNotNull(SysAiAppKey::getGroupId))
                .stream()
                .filter(k -> k.getId() != null && k.getGroupId() != null)
                .collect(Collectors.groupingBy(SysAiAppKey::getGroupId,
                        Collectors.mapping(k -> k.getId().longValue(), Collectors.toList())));
    }

    /**
     * 分组主键 Long → appKey 侧使用的 Integer（列类型是 int）。
     *
     * @param groupId 分组主键，可为 null
     * @return Integer 主键，入参为空时返回 null
     */
    private Integer intGroupId(Long groupId) {
        return groupId == null ? null : groupId.intValue();
    }

    /**
     * 实体转 DTO。
     *
     * @param entity    分组实体
     * @param appKeyIds 成员 Key 主键（来自真源；null 时回落到 JSON 快照列）
     * @return 分组 DTO
     */
    private AiGroupDTO toDto(SysAiGroup entity, List<Long> appKeyIds) {
        if (entity == null) {
            return null;
        }
        AiGroupDTO dto = new AiGroupDTO();
        dto.setId(entity.getGroupId());
        dto.setName(entity.getGroupName());
        dto.setDescription(entity.getGroupDescription());
        dto.setModels(parseStringList(entity.getGroupModels()));
        dto.setAppKeyIds(appKeyIds != null ? appKeyIds : parseLongList(entity.getGroupAppKeyIds()));
        dto.setCreatedAt(entity.getGroupCreateTime() != null ? entity.getGroupCreateTime().format(FORMATTER) : null);
        return dto;
    }

    /**
     * DTO 转实体。
     *
     * <p>只写调用方明确给出的字段：{@code updateById} 会跳过 null，
     * 于是「不传 models / appKeyIds」等于「不改」，不会把库里已有内容清空。</p>
     *
     * @param dto 分组 DTO
     * @return 分组实体
     */
    private SysAiGroup toEntity(AiGroupDTO dto) {
        SysAiGroup entity = new SysAiGroup();
        entity.setGroupName(dto.getName());
        entity.setGroupDescription(dto.getDescription());
        if (dto.getModels() != null) {
            entity.setGroupModels(Json.toJson(dto.getModels()));
        }
        if (dto.getAppKeyIds() != null) {
            entity.setGroupAppKeyIds(Json.toJson(dto.getAppKeyIds()));
        }
        return entity;
    }

    /**
     * 解析 JSON 字符串列表。
     *
     * @param content JSON 数组字符串
     * @return 字符串列表
     */
    private List<String> parseStringList(String content) {
        if (content == null || content.isBlank()) {
            return new ArrayList<>();
        }
        try {
            if (content.trim().startsWith("[")) {
                @SuppressWarnings("unchecked")
                List<String> list = Json.fromJson(content, List.class);
                return list != null ? list : new ArrayList<>();
            }
            return java.util.Arrays.stream(content.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        } catch (Exception e) {
            return java.util.Arrays.stream(content.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    /**
     * 解析 JSON Long 列表。
     *
     * <p>逐项按 {@link Number#longValue()} 转换：JSON 里的 {@code 1} 反序列化出来是
     * {@code Integer}，直接强转成 {@code List<Long>} 会在真正取值时抛
     * ClassCastException（泛型擦除把错误推到了使用点）。</p>
     *
     * @param content JSON 数组字符串
     * @return Long 列表
     */
    private List<Long> parseLongList(String content) {
        if (content == null || content.isBlank()) {
            return new ArrayList<>();
        }
        if (content.trim().startsWith("[")) {
            try {
                List<?> raw = Json.fromJson(content, List.class);
                List<Long> out = new ArrayList<>();
                if (raw != null) {
                    for (Object item : raw) {
                        if (item instanceof Number number) {
                            out.add(number.longValue());
                        }
                    }
                }
                return out;
            } catch (Exception e) {
                log.warn("[SysAiGroup] 解析 appKeyIds 失败，按空处理: {}", e.getMessage());
                return new ArrayList<>();
            }
        }
        try {
            return java.util.Arrays.stream(content.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .toList();
        } catch (Exception e) {
            log.warn("[SysAiGroup] 解析 appKeyIds 失败，按空处理: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
