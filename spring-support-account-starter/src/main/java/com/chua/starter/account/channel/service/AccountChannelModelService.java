package com.chua.starter.account.channel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.account.channel.cache.AccountChannelModelCache;
import com.chua.starter.account.channel.event.ChannelCatalogChangedEvent;
import com.chua.starter.account.channel.mapper.SysAccountChannelModelMapper;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 渠道模型清单业务（账号模块唯一实现）。
 *
 * <p>「渠道有哪些模型、哪些开着」是渠道资产的一部分，规则全部落在本类：</p>
 * <ul>
 *   <li><b>复合主键</b>：{@code (provider_id, model)} 唯一，同名模型可跨渠道存在；</li>
 *   <li><b>幂等补录</b>：批量导入只补不覆盖，已存在的模型保留用户改过的别名与参数；</li>
 *   <li><b>排序</b>：新模型一律追加到该渠道既有行之后，避免并列序号导致显示顺序抖动；</li>
 *   <li><b>改名</b>：复合主键变更 = 复制到新标识 + 删旧行，同一事务完成；</li>
 *   <li><b>删除</b>：先整批定位再删，任何一个渠道不存在都拦截整批。</li>
 * </ul>
 *
 * <p><b>边界</b>：模型的上下文窗口、价格、能力节点等<i>内容</i>由 AI 侧采集与计算，
 * 本类只负责把它们作为渠道资产落库，并广播变更事件；路由组的成员清理属于 AI 语义，
 * 由事件消费方处理。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountChannelModelService {

    /**
     * 渠道模型表
     */
    private final SysAccountChannelModelMapper modelMapper;
    /**
     * 变更事件发布器
     */
    private final ApplicationEventPublisher events;

    /**
     * 定位单条模型行，不存在返回 null。
     *
     * @param providerId 渠道标识
     * @param model      模型标识
     * @return 模型行或 null
     */
    public SysAccountChannelModel find(String providerId, String model) {
        if (providerId == null || model == null) {
            return null;
        }
        return modelMapper.selectOne(new LambdaQueryWrapper<SysAccountChannelModel>()
                .eq(SysAccountChannelModel::getProviderId, providerId)
                .eq(SysAccountChannelModel::getModel, model));
    }

    /**
     * 渠道下的全部模型行（不做排序保证，供批量处理使用）。
     *
     * @param providerId 渠道标识
     * @return 模型列表
     */
    public List<SysAccountChannelModel> list(String providerId) {
        return modelMapper.selectList(new LambdaQueryWrapper<SysAccountChannelModel>()
                .eq(SysAccountChannelModel::getProviderId, providerId));
    }

    /**
     * 渠道下已开启的模型行（对外供给口径）。
     *
     * <p>「停用的模型不对外供给」是渠道管理的业务规则，判定固定落在账号模块：
     * 会话选择、路由解析、上游清单一律只认 {@code enabled=1} 的行。</p>
     *
     * <p>与 {@link #listAll(String)} 的分工：本方法给消费方（只要可用的），
     * {@code listAll} 给管理页（需要看到停用行才能重新开启）。调用方不得自己再过一遍
     * {@code enabled}，否则同一口径会在多处实现。</p>
     *
     * <p><b>缓存</b>：结果按渠道进入账号模块的模型清单缓存（{@link AccountChannelModelCache}）。
     * 开关、改名、删除都在本类的写方法里广播变更事件，缓存于事务提交后整体失效；
     * 调用方不需要关心失效，也不得自己再缓存一份。</p>
     *
     * @param providerId 渠道标识
     * @return 已开启的模型列表
     */
    @Cacheable(cacheNames = AccountChannelModelCache.NAME,
            cacheResolver = AccountChannelModelCache.RESOLVER,
            key = "'enabled:' + #providerId")
    public List<SysAccountChannelModel> listEnabled(String providerId) {
        return modelMapper.selectList(new LambdaQueryWrapper<SysAccountChannelModel>()
                .eq(SysAccountChannelModel::getProviderId, providerId)
                .eq(SysAccountChannelModel::getEnabled, true));
    }

    /**
     * 按模型标识反查已开启的模型行（跨渠道同名模型全部返回，按渠道标识升序）。
     *
     * <p>路由解析「用户填的是裸模型名，要落到哪个渠道」就靠这份结果；它同样是
     * 模型状态的一部分，与 {@link #listEnabled(String)} 共用一份缓存。</p>
     *
     * @param model 模型标识
     * @return 已开启的模型行；入参为空时返回空列表
     */
    @Cacheable(cacheNames = AccountChannelModelCache.NAME,
            cacheResolver = AccountChannelModelCache.RESOLVER,
            key = "'model:' + #model")
    public List<SysAccountChannelModel> listEnabledByModel(String model) {
        if (model == null || model.isBlank()) {
            return List.of();
        }
        return modelMapper.selectList(new LambdaQueryWrapper<SysAccountChannelModel>()
                .eq(SysAccountChannelModel::getModel, model)
                .eq(SysAccountChannelModel::getEnabled, true)
                .orderByAsc(SysAccountChannelModel::getProviderId));
    }

    /**
     * 全渠道已开启的模型行（按渠道、排序号升序）。
     *
     * <p>「没有点名模型时，从哪些模型里挑一个」属于供给口径，判定同样落在账号模块：
     * 调用方拿到的必然是 {@code enabled=1} 的行，不得自己再过滤一遍。</p>
     *
     * @return 已开启的模型行；无数据时返回空列表
     */
    @Cacheable(cacheNames = AccountChannelModelCache.NAME,
            cacheResolver = AccountChannelModelCache.RESOLVER,
            key = "'enabled-all'")
    public List<SysAccountChannelModel> listAllEnabled() {
        return modelMapper.selectList(new LambdaQueryWrapper<SysAccountChannelModel>()
                .eq(SysAccountChannelModel::getEnabled, true)
                .orderByAsc(SysAccountChannelModel::getProviderId)
                .orderByAsc(SysAccountChannelModel::getSortOrder));
    }

    /**
     * 按模型名取一条已开启的模型行。
     *
     * <p>同一模型名可能挂在多条渠道下（同名不同价）。「取哪一条」由账号模块定：
     * 固定按渠道标识升序取第一条，保证同一份数据每次解析到同一渠道，
     * 而不是让数据库的返回顺序决定实际调用谁。</p>
     *
     * @param model 模型标识
     * @return 模型行；没有已开启的匹配行时返回 {@code null}
     */
    @Cacheable(cacheNames = AccountChannelModelCache.NAME,
            cacheResolver = AccountChannelModelCache.RESOLVER,
            key = "'first-enabled:' + #model",
            unless = "#result == null")
    public SysAccountChannelModel findEnabledByModel(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        List<SysAccountChannelModel> rows = modelMapper.selectList(
                new LambdaQueryWrapper<SysAccountChannelModel>()
                        .eq(SysAccountChannelModel::getModel, model)
                        .eq(SysAccountChannelModel::getEnabled, true)
                        .orderByAsc(SysAccountChannelModel::getProviderId)
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 指定渠道下排序最靠前的已开启模型。
     *
     * <p>调用方只拿到渠道标识（例如「用这个渠道先跑一下」）时，用哪个模型由账号模块定：
     * 固定取该渠道内排序号最小的一条，避免随数据库返回顺序漂移。</p>
     *
     * @param providerId 渠道标识
     * @return 模型行；该渠道没有已开启模型时返回 {@code null}
     */
    @Cacheable(cacheNames = AccountChannelModelCache.NAME,
            cacheResolver = AccountChannelModelCache.RESOLVER,
            key = "'first-enabled-provider:' + #providerId",
            unless = "#result == null")
    public SysAccountChannelModel findFirstEnabled(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return null;
        }
        List<SysAccountChannelModel> rows = modelMapper.selectList(
                new LambdaQueryWrapper<SysAccountChannelModel>()
                        .eq(SysAccountChannelModel::getProviderId, providerId)
                        .eq(SysAccountChannelModel::getEnabled, true)
                        .orderByAsc(SysAccountChannelModel::getSortOrder)
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 全部模型行（不分渠道，按排序号、创建时间升序）。
     *
     * <p>含停用行，供「模型总览 / 指标快照」这类需要看全量的场景使用；
     * 只想要可用模型的走 {@link #listAllEnabled()}。</p>
     *
     * @return 模型行
     */
    public List<SysAccountChannelModel> listAll() {
        return modelMapper.selectList(new LambdaQueryWrapper<SysAccountChannelModel>()
                .orderByAsc(SysAccountChannelModel::getSortOrder)
                .orderByAsc(SysAccountChannelModel::getCreatedAt));
    }

    /**
     * 指定渠道下按模型名取已开启的模型行。
     *
     * @param providerId 渠道标识
     * @param model      模型标识
     * @return 已开启的模型行；不存在或已停用时返回 {@code null}
     */
    public SysAccountChannelModel findEnabled(String providerId, String model) {
        SysAccountChannelModel row = find(providerId, model);
        return row != null && Boolean.TRUE.equals(row.getEnabled()) ? row : null;
    }

    /**
     * 按模型名取任意一条模型行（不看开关）。
     *
     * <p>用于读取上文档位、能力等元数据 —— 这些信息与「能不能用」无关，
     * 模型停用后仍然要能读到。同名模型跨渠道存在时按渠道标识升序取第一条。</p>
     *
     * @param model 模型标识
     * @return 模型行；不存在时返回 {@code null}
     */
    public SysAccountChannelModel findAnyByModel(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        List<SysAccountChannelModel> rows = modelMapper.selectList(
                new LambdaQueryWrapper<SysAccountChannelModel>()
                        .eq(SysAccountChannelModel::getModel, model)
                        .orderByAsc(SysAccountChannelModel::getProviderId)
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 指定渠道下的模型是否已开启。
     *
     * @param providerId 渠道标识
     * @param model      模型标识
     * @return 已开启为 {@code true}
     */
    public boolean isEnabled(String providerId, String model) {
        return findEnabled(providerId, model) != null;
    }

    /**
     * 是否存在该名称的已开启模型（任意渠道）。
     *
     * @param model 模型标识
     * @return 存在为 {@code true}
     */
    @Cacheable(cacheNames = AccountChannelModelCache.NAME,
            cacheResolver = AccountChannelModelCache.RESOLVER,
            key = "'model-enabled:' + #model")
    public boolean isEnabledByModel(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        return modelMapper.selectCount(new LambdaQueryWrapper<SysAccountChannelModel>()
                .eq(SysAccountChannelModel::getModel, model)
                .eq(SysAccountChannelModel::getEnabled, true)) > 0;
    }

    /**
     * 按模型名关键字检索已开启的模型行（供「挑成员」这类选择器使用）。
     *
     * <p>条数上限由账号模块兜住，避免选择器一次把整张模型表拉进内存。</p>
     *
     * @param keyword 关键字，空表示不过滤
     * @param limit   最多返回条数（&lt;=0 取默认 50，最大 500）
     * @return 模型行，按模型名升序
     */
    public List<SysAccountChannelModel> searchEnabled(String keyword, int limit) {
        LambdaQueryWrapper<SysAccountChannelModel> wrapper =
                new LambdaQueryWrapper<SysAccountChannelModel>()
                        .eq(SysAccountChannelModel::getEnabled, true)
                        .orderByAsc(SysAccountChannelModel::getModel)
                        .last("LIMIT " + (limit <= 0 ? 50 : Math.min(limit, 500)));
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SysAccountChannelModel::getModel, keyword.trim());
        }
        return modelMapper.selectList(wrapper);
    }

    /**
     * 全部模型行（可按渠道过滤，按创建时间升序）。
     *
     * <p>管理页口径：含停用行，否则停用的模型在界面上消失后再也无法重新开启。
     * <b>不缓存</b>：管理面刚提交的改动必须立刻可见，而供给侧（{@link #listEnabled(String)}）
     * 用缓存换吞吐；两边的时效要求不同，不共用同一份快照。</p>
     *
     * @param providerId 渠道标识，null 表示不过滤
     * @return 模型列表
     */
    public List<SysAccountChannelModel> listAll(String providerId) {
        LambdaQueryWrapper<SysAccountChannelModel> wrapper = new LambdaQueryWrapper<>();
        if (providerId != null) {
            wrapper.eq(SysAccountChannelModel::getProviderId, providerId);
        }
        wrapper.orderByAsc(SysAccountChannelModel::getCreatedAt);
        return modelMapper.selectList(wrapper);
    }

    /**
     * 该渠道当前最大排序号（无行时返回 -1）。
     *
     * @param providerId 渠道标识
     * @return 最大排序号
     */
    public int maxSortOrder(String providerId) {
        int max = -1;
        for (SysAccountChannelModel row : list(providerId)) {
            if (row.getSortOrder() != null && row.getSortOrder() > max) {
                max = row.getSortOrder();
            }
        }
        return max;
    }

    /**
     * 统计某个模型标识被多少条渠道引用（用于判断裸模型名是否仍被占用）。
     *
     * @param model 模型标识
     * @return 引用条数
     */
    public long countByModel(String model) {
        if (model == null || model.isBlank()) {
            return 0L;
        }
        return modelMapper.selectCount(new LambdaQueryWrapper<SysAccountChannelModel>()
                .eq(SysAccountChannelModel::getModel, model));
    }

    /**
     * 新增单条模型行。
     *
     * @param row 模型行（{@code providerId}、{@code model} 必填）
     * @return 落库后的模型行
     */
    @Transactional(rollbackFor = Exception.class)
    public SysAccountChannelModel create(SysAccountChannelModel row) {
        if (row == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "model 不能为空");
        }
        if (row.getProviderId() == null || row.getProviderId().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "provider_id 不能为空");
        }
        if (row.getModel() == null || row.getModel().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "model 不能为空");
        }
        if (find(row.getProviderId(), row.getModel()) != null) {
            throw new ApiException(ApiErrorCode.CONFLICT, "模型 ID 已存在: " + row.getModel());
        }
        long now = System.currentTimeMillis();
        if (row.getEnabled() == null) {
            row.setEnabled(Boolean.TRUE);
        }
        if (row.getSortOrder() == null) {
            row.setSortOrder(maxSortOrder(row.getProviderId()) + 1);
        }
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        modelMapper.insert(row);
        events.publishEvent(ChannelCatalogChangedEvent.catalog(
                "channel-model-created:" + row.getProviderId() + ":" + row.getModel()));
        return row;
    }

    /**
     * 批量补录模型（幂等：已存在的不覆盖，只补新出现的）。
     *
     * @param providerId 渠道标识
     * @param candidates 待补录的模型行，内容由调用方计算好
     * @return 实际新增条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int createMissing(String providerId, List<SysAccountChannelModel> candidates) {
        if (providerId == null || providerId.isBlank() || candidates == null || candidates.isEmpty()) {
            return 0;
        }
        Set<String> existing = new HashSet<>(16);
        for (SysAccountChannelModel row : list(providerId)) {
            if (row.getModel() != null) {
                existing.add(row.getModel());
            }
        }
        int sort = maxSortOrder(providerId) + 1;
        long now = System.currentTimeMillis();
        int imported = 0;
        for (SysAccountChannelModel candidate : candidates) {
            if (candidate == null || candidate.getModel() == null || candidate.getModel().isBlank()) {
                continue;
            }
            // Set#add 同时挡掉上游内部重名，避免同批插入两条
            if (!existing.add(candidate.getModel())) {
                continue;
            }
            candidate.setProviderId(providerId);
            if (candidate.getEnabled() == null) {
                candidate.setEnabled(Boolean.TRUE);
            }
            candidate.setSortOrder(sort++);
            candidate.setCreatedAt(now);
            candidate.setUpdatedAt(now);
            modelMapper.insert(candidate);
            imported++;
        }
        if (imported > 0) {
            events.publishEvent(ChannelCatalogChangedEvent.catalog(
                    "channel-models-imported:" + providerId + ":" + imported));
        }
        return imported;
    }

    /**
     * 编辑语义的保存：可空列显式写入，允许把已配置的值改回空。
     *
     * @param row 合并好的模型行
     * @return 保存后的模型行
     */
    @Transactional(rollbackFor = Exception.class)
    public SysAccountChannelModel saveEdited(SysAccountChannelModel row) {
        if (row == null || row.getProviderId() == null || row.getModel() == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "provider_id / model 不能为空");
        }
        row.setUpdatedAt(System.currentTimeMillis());
        // 复合主键由渠道和模型共同确定，避免同名模型跨渠道误更新；
        // 可空字段显式 set，确保传入 null 时能够清空已有配置。
        modelMapper.update(row, new LambdaUpdateWrapper<SysAccountChannelModel>()
                .eq(SysAccountChannelModel::getProviderId, row.getProviderId())
                .eq(SysAccountChannelModel::getModel, row.getModel())
                .set(SysAccountChannelModel::getAlias, row.getAlias())
                // 描述也在可空清单里：编辑时清空描述要能写回 NULL，
                // 漏掉这行 MP 会按默认策略跳过 null 字段，旧描述永远删不掉
                .set(SysAccountChannelModel::getDescription, row.getDescription())
                .set(SysAccountChannelModel::getContextWindow, row.getContextWindow())
                .set(SysAccountChannelModel::getMaxTokens, row.getMaxTokens())
                .set(SysAccountChannelModel::getTemperature, row.getTemperature())
                .set(SysAccountChannelModel::getTopP, row.getTopP())
                .set(SysAccountChannelModel::getFrequencyPenalty, row.getFrequencyPenalty()));
        events.publishEvent(ChannelCatalogChangedEvent.catalog(
                "channel-model-updated:" + row.getProviderId() + ":" + row.getModel()));
        return row;
    }

    /**
     * 局部更新模型行（只写非空列，用于元数据回填）。
     *
     * @param row 已合并的模型行
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateByKey(SysAccountChannelModel row) {
        if (row == null || row.getProviderId() == null || row.getModel() == null) {
            return 0;
        }
        row.setUpdatedAt(System.currentTimeMillis());
        int updated = modelMapper.updateByKey(row);
        if (updated > 0) {
            events.publishEvent(ChannelCatalogChangedEvent.catalog(
                    "channel-model-metadata:" + row.getProviderId() + ":" + row.getModel()));
        }
        return updated;
    }

    /**
     * 开关单个模型。
     *
     * @param providerId 渠道标识
     * @param model      模型标识
     * @param enabled    是否启用
     */
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(String providerId, String model, boolean enabled) {
        SysAccountChannelModel row = find(providerId, model);
        if (row == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider model 不存在: " + model);
        }
        row.setEnabled(enabled);
        row.setUpdatedAt(System.currentTimeMillis());
        modelMapper.updateByKey(row);
        events.publishEvent(ChannelCatalogChangedEvent.catalog(
                "channel-model-enabled:" + providerId + ":" + model + "=" + enabled));
    }

    /**
     * 重命名模型标识（复合主键变更：复制到新标识并删除旧行，同一事务）。
     *
     * @param providerId 渠道标识
     * @param oldName    原模型标识
     * @param newName    新模型标识
     * @return 改名后的模型行
     */
    @Transactional(rollbackFor = Exception.class)
    public SysAccountChannelModel rename(String providerId, String oldName, String newName) {
        if (newName != null && newName.equals(oldName)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "新模型 ID 不能与原 ID 相同");
        }
        SysAccountChannelModel existing = find(providerId, oldName);
        if (existing == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider model 不存在: " + oldName);
        }
        if (find(providerId, newName) != null) {
            throw new ApiException(ApiErrorCode.CONFLICT, "模型 ID 已存在: " + newName);
        }
        existing.setModel(newName);
        existing.setUpdatedAt(System.currentTimeMillis());
        modelMapper.insert(existing);
        modelMapper.delete(new LambdaQueryWrapper<SysAccountChannelModel>()
                .eq(SysAccountChannelModel::getProviderId, providerId)
                .eq(SysAccountChannelModel::getModel, oldName));
        events.publishEvent(ChannelCatalogChangedEvent.catalog(
                "channel-model-renamed:" + providerId + ":" + oldName + "->" + newName));
        return existing;
    }

    /**
     * 清理模型标识以 {@code /} 开头的历史行。
     *
     * <p>早期版本把本地模型的绝对路径直接写进了 {@code model} 列，而
     * {@code (provider_id, model)} 是表主键：改主键字段不能靠 update 修，
     * 只能按旧值删掉再以新模型名重建。这属于本表的数据修复规则，所以落在账号模块，
     * 调用方只表达「帮我清掉这个渠道的遗留行」，不自己拼删除条件。</p>
     *
     * @param providerId 渠道标识
     * @return 清理条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int purgeLegacyPathModels(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return 0;
        }
        int purged = modelMapper.delete(new LambdaQueryWrapper<SysAccountChannelModel>()
                .eq(SysAccountChannelModel::getProviderId, providerId)
                .likeRight(SysAccountChannelModel::getModel, "/"));
        if (purged > 0) {
            events.publishEvent(ChannelCatalogChangedEvent.catalog(
                    "channel-models-purged:" + providerId + ":" + purged));
        }
        return purged;
    }

    /**
     * 删除单条模型。
     *
     * @param providerId 渠道标识
     * @param model      模型标识
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String providerId, String model) {
        int deleted = modelMapper.delete(new LambdaQueryWrapper<SysAccountChannelModel>()
                .eq(SysAccountChannelModel::getProviderId, providerId)
                .eq(SysAccountChannelModel::getModel, model));
        if (deleted == 0) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider model 不存在: " + model);
        }
        events.publishEvent(ChannelCatalogChangedEvent.catalog(
                "channel-model-removed:" + providerId + ":" + model));
    }

    /**
     * 批量删除模型（复合 id 形如 {@code providerId:modelName}）。
     *
     * <p>先整批解析与定位，任何一条非法或不存在的渠道都拦截整批，不出现部分删除。</p>
     *
     * @param compositeIds 复合标识列表
     * @return 实际删除条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteBatch(List<String> compositeIds) {
        if (compositeIds == null || compositeIds.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ids 不能为空");
        }
        List<ModelKey> keys = new ArrayList<>(compositeIds.size());
        for (String id : compositeIds) {
            ModelKey key = ModelKey.parse(id);
            if (key == null) {
                continue;
            }
            keys.add(key);
        }
        if (keys.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "无有效的模型ID");
        }
        int deleted = 0;
        for (ModelKey key : keys) {
            int removed = modelMapper.delete(new LambdaQueryWrapper<SysAccountChannelModel>()
                    .eq(SysAccountChannelModel::getProviderId, key.providerId())
                    .eq(SysAccountChannelModel::getModel, key.model()));
            if (removed > 0) {
                deleted++;
                events.publishEvent(ChannelCatalogChangedEvent.catalog(
                        "channel-model-removed:" + key.providerId() + ":" + key.model()));
            }
        }
        return deleted;
    }

    /**
     * 模型复合标识。
     *
     * @param providerId 渠道标识
     * @param model      模型标识
     * @author CH
     */
    public record ModelKey(String providerId, String model) {

        /**
         * 解析 {@code providerId:modelName}。
         *
         * @param composite 复合标识
         * @return 解析结果，格式非法返回 null
         */
        public static ModelKey parse(String composite) {
            if (composite == null || composite.isBlank()) {
                return null;
            }
            int separator = composite.indexOf(':');
            if (separator <= 0) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM,
                        "模型ID格式非法，应为 providerId:modelName");
            }
            String providerId = composite.substring(0, separator);
            String model = composite.substring(separator + 1);
            if (providerId.isBlank() || model.isBlank()) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM,
                        "模型ID格式非法，应为 providerId:modelName");
            }
            return new ModelKey(providerId, model);
        }
    }
}
