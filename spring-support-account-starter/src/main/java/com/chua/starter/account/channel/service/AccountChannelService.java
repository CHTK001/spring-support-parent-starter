package com.chua.starter.account.channel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.account.channel.event.ChannelCatalogChangedEvent;
import com.chua.starter.account.channel.mapper.SysAccountChannelConnectionMapper;
import com.chua.starter.account.channel.mapper.SysAccountChannelMapper;
import com.chua.starter.account.channel.mapper.SysAccountChannelModelMapper;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.account.channel.store.SysAccountChannelConnection;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.support.AccountUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * 渠道生命周期业务（账号模块唯一实现）。
 *
 * <p>本类持有「渠道是什么、能被怎么改」的全部规则，是渠道资产的唯一写入口：</p>
 * <ul>
 *   <li><b>标识</b>：{@code provider_id} 全局唯一，落库前必须为空校验 + 存在性校验；</li>
 *   <li><b>判重</b>：同平台下渠道名称去空格、忽略大小写后不得重复；</li>
 *   <li><b>级联</b>：删除渠道必须连带删除其模型清单与<b>全部</b>连接；</li>
 *   <li><b>克隆</b>：连同模型清单与当前账号可见的连接深拷贝一份；</li>
 *   <li><b>协议</b>：接入协议落在每个模型的能力节点上，改渠道协议要整渠道同步；</li>
 *   <li><b>归属</b>：渠道行是全局的，渠道下的连接凭证属于绑定它的账号，读路径按归属过滤。</li>
 * </ul>
 *
 * <p><b>边界</b>：本类不知道路由、探活、指标、ONNX 物化、全局默认模型这些 AI 语义。
 * 删除/改写产生的连带影响通过 {@link ChannelCatalogChangedEvent} 广播，由 AI 侧自行处理
 * （清路由缓存、复位健康状态、清理悬空的全局默认模型、刷新推理设备）。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountChannelService {

    /**
     * 渠道表
     */
    private final SysAccountChannelMapper channelMapper;
    /**
     * 渠道模型表
     */
    private final SysAccountChannelModelMapper modelMapper;
    /**
     * 渠道连接表
     */
    private final SysAccountChannelConnectionMapper connectionMapper;
    /**
     * 变更事件发布器
     */
    private final ApplicationEventPublisher events;

    /**
     * 定位渠道，不存在抛 {@code NOT_FOUND}。
     *
     * @param providerId 渠道标识
     * @return 渠道实体
     */
    public SysAccountChannel require(String providerId) {
        SysAccountChannel entity = providerId == null ? null : channelMapper.selectById(providerId);
        if (entity == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "channel 不存在: " + providerId);
        }
        return entity;
    }

    /**
     * 渠道是否存在。
     *
     * @param providerId 渠道标识
     * @return 存在返回 true
     */
    public boolean exists(String providerId) {
        return providerId != null && channelMapper.selectById(providerId) != null;
    }

    /**
     * 全量渠道列表（按排序号、创建时间升序）。
     *
     * @return 渠道列表
     */
    public List<SysAccountChannel> listAll() {
        return channelMapper.selectList(new LambdaQueryWrapper<SysAccountChannel>()
                .orderByAsc(SysAccountChannel::getSortOrder)
                .orderByAsc(SysAccountChannel::getCreatedAt));
    }

    /**
     * 按平台取渠道列表。
     *
     * @param platform 平台标识
     * @return 渠道列表
     */
    public List<SysAccountChannel> listByPlatform(String platform) {
        return channelMapper.selectList(new LambdaQueryWrapper<SysAccountChannel>()
                .eq(SysAccountChannel::getPlatform, platform));
    }

    /**
     * 定位渠道，不存在返回 {@code null}。
     *
     * <p>与 {@link #require(String)} 是同一件事的两种口径：调用方需要自行决定
     * 「不存在」是错误还是分支（例如路由解析时跳过），用本方法；必须拦截的用 require。</p>
     *
     * @param providerId 渠道标识
     * @return 渠道实体；不存在返回 {@code null}
     */
    public SysAccountChannel find(String providerId) {
        return providerId == null || providerId.isBlank() ? null : channelMapper.selectById(providerId);
    }

    /**
     * 按平台标识取第一条渠道（同平台可能有多条，取排序最靠前的一条）。
     *
     * <p>用于「只记得平台名、不记得 provider_id」的兜底定位（例如 magpie 网关、
     * 免登录渠道这类按平台固定接入的上游）。</p>
     *
     * @param platform 平台标识
     * @return 渠道实体；未命中返回 {@code null}
     */
    public SysAccountChannel findByPlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }
        List<SysAccountChannel> rows = channelMapper.selectList(new LambdaQueryWrapper<SysAccountChannel>()
                .eq(SysAccountChannel::getPlatform, platform)
                .orderByAsc(SysAccountChannel::getSortOrder)
                .orderByAsc(SysAccountChannel::getCreatedAt));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 全部已启用的渠道（按排序号、创建时间升序）。
     *
     * <p>「停用的渠道不对外供给」是渠道管理的口径，固定落在本模块；
     * 消费方拿到的一定是已启用的行，不需要自己再判一次 {@code enabled}。</p>
     *
     * @return 已启用的渠道列表
     */
    public List<SysAccountChannel> listEnabled() {
        return channelMapper.selectList(new LambdaQueryWrapper<SysAccountChannel>()
                .eq(SysAccountChannel::getEnabled, true)
                .orderByAsc(SysAccountChannel::getSortOrder)
                .orderByAsc(SysAccountChannel::getCreatedAt));
    }

    /**
     * 校验指定渠道模型当前可供给，所有消费方复用同一启用口径。
     * @param providerId 渠道标识
     * @param model 模型标识
     */
    public void requireAvailableModel(String providerId, String model) {
        SysAccountChannel channel = require(providerId);
        if (!Boolean.TRUE.equals(channel.getEnabled()) || model == null || model.isBlank()
                || modelMapper.selectCount(new LambdaQueryWrapper<SysAccountChannelModel>()
                    .eq(SysAccountChannelModel::getProviderId, providerId)
                    .eq(SysAccountChannelModel::getModel, model)
                    .eq(SysAccountChannelModel::getEnabled, true)) == 0) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "渠道或模型未启用");
        }
    }

    /**
     * 按认证方式取渠道（不看开关）。
     *
     * @param authScheme 认证方式，如 {@code oauth}
     * @return 渠道列表
     */
    public List<SysAccountChannel> listByAuthScheme(String authScheme) {
        return channelMapper.selectList(new LambdaQueryWrapper<SysAccountChannel>()
                .eq(SysAccountChannel::getAuthScheme, authScheme));
    }

    /**
     * 按认证方式取已启用的渠道。
     *
     * @param authScheme 认证方式，如 {@code oauth}
     * @return 已启用的渠道列表
     */
    public List<SysAccountChannel> listEnabledByAuthScheme(String authScheme) {
        return channelMapper.selectList(new LambdaQueryWrapper<SysAccountChannel>()
                .eq(SysAccountChannel::getAuthScheme, authScheme)
                .eq(SysAccountChannel::getEnabled, true));
    }

    /**
     * 平台标识包含关键字的渠道（大小写不敏感，用于按平台名兜底定位渠道）。
     *
     * @param keyword 平台标识片段
     * @return 渠道列表
     */
    public List<SysAccountChannel> listByPlatformLike(String keyword) {
        return channelMapper.selectList(new LambdaQueryWrapper<SysAccountChannel>()
                .like(SysAccountChannel::getPlatform, keyword));
    }

    /**
     * 按渠道名称取已启用的渠道（大小写不敏感地精确匹配）。
     *
     * @param name 渠道名称
     * @return 渠道实体；未命中返回 {@code null}
     */
    public SysAccountChannel findEnabledByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        List<SysAccountChannel> rows = listEnabledByName(name);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 按渠道名称取全部已启用的渠道（同名多策略，如路由组）。
     *
     * @param name 渠道名称
     * @return 已启用的渠道列表
     */
    public List<SysAccountChannel> listEnabledByName(String name) {
        if (name == null || name.isBlank()) {
            return java.util.Collections.emptyList();
        }
        return channelMapper.selectList(new LambdaQueryWrapper<SysAccountChannel>()
                .eq(SysAccountChannel::getName, name)
                .eq(SysAccountChannel::getEnabled, true)
                .orderByAsc(SysAccountChannel::getSortOrder)
                .orderByAsc(SysAccountChannel::getCreatedAt));
    }

    /**
     * 只更新渠道的 {@code extra} 配置（路由组设置、上游限制等 JSON 列）。
     *
     * <p>整列覆盖式写入，调用方负责先把原配置合并好；这是渠道资产的元数据，
     * 落库后广播变更事件，让消费方失效各自的快照。</p>
     *
     * @param providerId 渠道标识
     * @param extra      新的配置
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateExtra(String providerId, java.util.Map<String, Object> extra) {
        if (providerId == null || providerId.isBlank()) {
            return 0;
        }
        SysAccountChannel update = new SysAccountChannel();
        update.setProviderId(providerId);
        update.setExtra(extra);
        update.setUpdatedAt(System.currentTimeMillis());
        int updated = channelMapper.updateById(update);
        if (updated > 0) {
            events.publishEvent(ChannelCatalogChangedEvent.catalog("channel-extra:" + providerId));
        }
        return updated;
    }

    /**
     * 更新渠道的接入端点（基础地址 / 认证方式 / 凭证），未传的字段保持原值。
     *
     * <p>用于「渠道行已存在但端点信息缺失」的补齐场景：只回填调用方给出的字段，
     * 不覆盖管理员已经改过的其它配置。</p>
     *
     * @param providerId  渠道标识
     * @param baseUrl     基础地址，null 表示不改
     * @param authScheme  认证方式，null 表示不改
     * @param credentials 凭证，null 表示不改
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateEndpoint(String providerId, String baseUrl, String authScheme,
                              java.util.Map<String, Object> credentials) {
        if (providerId == null || providerId.isBlank()) {
            return 0;
        }
        SysAccountChannel update = new SysAccountChannel();
        update.setProviderId(providerId);
        update.setBaseUrl(baseUrl);
        update.setAuthScheme(authScheme);
        update.setCredentials(credentials);
        update.setUpdatedAt(System.currentTimeMillis());
        int updated = channelMapper.updateById(update);
        if (updated > 0) {
            events.publishEvent(ChannelCatalogChangedEvent.catalog("channel-endpoint:" + providerId));
        }
        return updated;
    }

    /**
     * 下一个排序号（追加到末尾）。
     *
     * @return 排序号
     */
    public int nextSortOrder() {
        return channelMapper.selectCount(null).intValue();
    }

    /**
     * 新建渠道（含初始模型与初始连接）。
     *
     * @param entity            渠道实体，{@code providerId} 允许为空（由本方法生成）
     * @param initialModel      初始模型，可为 null
     * @param connections       初始连接，可为 null
     * @param allowDuplicateName 是否允许同平台重名（路由组这类「同平台多策略」需要）
     * @return 落库后的渠道实体
     */
    @Transactional(rollbackFor = Exception.class)
    public SysAccountChannel create(SysAccountChannel entity,
                                    SysAccountChannelModel initialModel,
                                    List<SysAccountChannelConnection> connections,
                                    boolean allowDuplicateName) {
        if (entity == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "channel 不能为空");
        }
        String platform = requireText(entity.getPlatform(), "platform");
        String name = requireText(entity.getName(), "name");
        entity.setPlatform(platform);
        entity.setName(name);
        if (entity.getProviderId() == null || entity.getProviderId().isBlank()) {
            entity.setProviderId(String.valueOf(System.currentTimeMillis() + (int) (Math.random() * 1000)));
        }
        if (exists(entity.getProviderId())) {
            throw new ApiException(ApiErrorCode.CONFLICT, "provider_id 已存在: " + entity.getProviderId());
        }
        if (!allowDuplicateName) {
            requireNotDuplicate(platform, name, null);
        }
        long now = System.currentTimeMillis();
        entity.setEnabled(entity.getEnabled() == null || entity.getEnabled());
        entity.setSortOrder(entity.getSortOrder() == null ? nextSortOrder() : entity.getSortOrder());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        channelMapper.insert(entity);

        if (initialModel != null) {
            initialModel.setProviderId(entity.getProviderId());
            initialModel.setCreatedAt(now);
            initialModel.setUpdatedAt(now);
            if (initialModel.getEnabled() == null) {
                initialModel.setEnabled(Boolean.TRUE);
            }
            modelMapper.insert(initialModel);
        }
        if (connections != null) {
            for (SysAccountChannelConnection conn : connections) {
                if (conn == null) {
                    continue;
                }
                conn.setProviderId(entity.getProviderId());
                if (conn.getConnectionId() == null || conn.getConnectionId().isBlank()) {
                    conn.setConnectionId(String.valueOf(System.currentTimeMillis() + (int) (Math.random() * 1000)));
                }
                conn.setCreatedAt(now);
                conn.setUpdatedAt(now);
                connectionMapper.insert(conn);
            }
        }
        events.publishEvent(ChannelCatalogChangedEvent.catalog("channel-created:" + entity.getProviderId()));
        return entity;
    }

    /**
     * 保存渠道行（字段映射由调用方完成，这里负责业务规则与落库）。
     *
     * @param entity         已合并好的渠道实体
     * @param checkDuplicate 是否做同平台重名校验（名称/平台有变更时为 true）
     * @param protocol       新的接入协议，null 表示不改
     * @return 落库后的渠道实体
     */
    @Transactional(rollbackFor = Exception.class)
    public SysAccountChannel save(SysAccountChannel entity, boolean checkDuplicate, String protocol) {
        if (entity == null || entity.getProviderId() == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "provider_id 不能为空");
        }
        require(entity.getProviderId());
        if (checkDuplicate) {
            requireNotDuplicate(entity.getPlatform(), entity.getName(), entity.getProviderId());
        }
        if (protocol != null) {
            syncProtocol(entity.getProviderId(), protocol);
        }
        entity.setUpdatedAt(System.currentTimeMillis());
        channelMapper.updateById(entity);
        events.publishEvent(ChannelCatalogChangedEvent.catalog("channel-updated:" + entity.getProviderId()));
        return entity;
    }

    /**
     * 删除渠道（含级联）。
     *
     * <p>整批先定位再删，任何一个不存在都拦截整批，不出现部分删除。</p>
     *
     * @param providerIds 渠道标识列表
     * @return 实际删除条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(List<String> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return 0;
        }
        List<SysAccountChannel> targets = new ArrayList<>(providerIds.size());
        for (String providerId : providerIds) {
            if (providerId == null || providerId.isBlank()) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "id 不能为空");
            }
            targets.add(require(providerId));
        }
        int deleted = 0;
        for (SysAccountChannel target : targets) {
            String providerId = target.getProviderId();
            List<String> ownedModels = modelsOf(providerId).stream()
                    .map(SysAccountChannelModel::getModel)
                    .filter(model -> model != null && !model.isBlank())
                    .map(String::trim)
                    .toList();
            channelMapper.deleteById(providerId);
            modelMapper.delete(new LambdaQueryWrapper<SysAccountChannelModel>()
                    .eq(SysAccountChannelModel::getProviderId, providerId));
            // 渠道行是全局的，它没了之后其下所有连接都不可用：连同别人名下的
            // 凭证一起清掉，避免同 id 重建渠道时把旧凭证「复活」。
            connectionMapper.delete(new LambdaQueryWrapper<SysAccountChannelConnection>()
                    .eq(SysAccountChannelConnection::getProviderId, providerId));
            deleted++;
            events.publishEvent(ChannelCatalogChangedEvent.catalog("channel-removed:" + providerId));
            log.info("删除渠道 {}，级联删除模型 {} 条与全部连接", providerId, ownedModels.size());
        }
        return deleted;
    }

    /**
     * 克隆渠道（深拷贝模型清单与当前账号可见的连接）。
     *
     * @param sourceProviderId 源渠道标识
     * @param cloneName        副本名称，为空时用「原名 副本」
     * @return 克隆出的渠道实体
     */
    @Transactional(rollbackFor = Exception.class)
    public SysAccountChannel clone(String sourceProviderId, String cloneName) {
        SysAccountChannel source = require(sourceProviderId);
        String name = cloneName == null || cloneName.isBlank() ? source.getName() + " 副本" : cloneName;
        requireNotDuplicate(source.getPlatform(), name, source.getProviderId());
        long now = System.currentTimeMillis();
        SysAccountChannel clone = new SysAccountChannel();
        clone.setProviderId(String.valueOf(System.currentTimeMillis() + (int) (Math.random() * 1000)));
        clone.setPlatform(source.getPlatform());
        clone.setName(name);
        clone.setBaseUrl(source.getBaseUrl());
        clone.setAuthScheme(source.getAuthScheme());
        clone.setCredentials(source.getCredentials() != null
                ? new LinkedHashMap<>(source.getCredentials()) : null);
        clone.setEnabled(source.getEnabled());
        clone.setBedrockConfig(source.getBedrockConfig() != null
                ? new LinkedHashMap<>(source.getBedrockConfig()) : null);
        clone.setProxy(source.getProxy());
        clone.setSortOrder(nextSortOrder());
        clone.setCreatedAt(now);
        clone.setUpdatedAt(now);
        channelMapper.insert(clone);

        for (SysAccountChannelModel src : modelsOf(sourceProviderId)) {
            SysAccountChannelModel copied = new SysAccountChannelModel();
            copied.setProviderId(clone.getProviderId());
            copied.setModel(src.getModel());
            copied.setAlias(src.getAlias());
            copied.setEnabled(src.getEnabled());
            copied.setSortOrder(src.getSortOrder());
            copied.setDescription(src.getDescription());
            copied.setCapabilities(src.getCapabilities() != null ? new ArrayList<>(src.getCapabilities()) : null);
            copied.setCreatedAt(now);
            copied.setUpdatedAt(now);
            modelMapper.insert(copied);
        }
        for (SysAccountChannelConnection src : connectionsOf(sourceProviderId)) {
            SysAccountChannelConnection copied = new SysAccountChannelConnection();
            copied.setConnectionId(String.valueOf(System.currentTimeMillis() + (int) (Math.random() * 1000)));
            copied.setProviderId(clone.getProviderId());
            copied.setRole(src.getRole());
            copied.setAccountType(src.getAccountType());
            copied.setLabel(src.getLabel());
            // 归属沿用源行：克隆是「复制一份同配置」，系统池的行仍留在系统池。
            copied.setUserId(src.getUserId());
            copied.setBaseUrl(src.getBaseUrl());
            copied.setAuthScheme(src.getAuthScheme());
            copied.setCredentials(src.getCredentials() != null
                    ? new LinkedHashMap<>(src.getCredentials()) : null);
            copied.setExtra(src.getExtra());
            copied.setCreatedAt(now);
            copied.setUpdatedAt(now);
            connectionMapper.insert(copied);
        }
        events.publishEvent(ChannelCatalogChangedEvent.catalog("channel-cloned:" + clone.getProviderId()));
        return clone;
    }

    /**
     * 同平台重名校验。
     *
     * <p>渠道列表里出现两个同名条目会让默认模型选择产生歧义，因此新增、编辑、克隆
     * 三条写入路径共用这一条规则。{@code excludeProviderId} 用于编辑/克隆时跳过自身。</p>
     *
     * @param platform          平台标识
     * @param name              渠道名称
     * @param excludeProviderId 跳过的渠道标识，可为 null
     */
    public void requireNotDuplicate(String platform, String name, String excludeProviderId) {
        String nameKey = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (nameKey.isEmpty()) {
            return;
        }
        for (SysAccountChannel candidate : listByPlatform(platform)) {
            if (excludeProviderId != null && excludeProviderId.equals(candidate.getProviderId())) {
                continue;
            }
            String candidateName = candidate.getName() == null ? ""
                    : candidate.getName().trim().toLowerCase(Locale.ROOT);
            if (nameKey.equals(candidateName)) {
                throw new ApiException(ApiErrorCode.CONFLICT, "供应商已存在，不允许重复新增: " + name);
            }
        }
    }

    /**
     * 渠道下的模型清单（按排序号、创建时间升序）。
     *
     * @param providerId 渠道标识
     * @return 模型列表
     */
    public List<SysAccountChannelModel> modelsOf(String providerId) {
        return modelMapper.selectList(new LambdaQueryWrapper<SysAccountChannelModel>()
                .eq(SysAccountChannelModel::getProviderId, providerId)
                .orderByAsc(SysAccountChannelModel::getSortOrder)
                .orderByAsc(SysAccountChannelModel::getCreatedAt));
    }

    /**
     * 渠道下当前账号可见的连接（按创建时间升序）。
     *
     * @param providerId 渠道标识
     * @return 连接列表
     */
    public List<SysAccountChannelConnection> connectionsOf(String providerId) {
        LambdaQueryWrapper<SysAccountChannelConnection> wrapper =
                new LambdaQueryWrapper<SysAccountChannelConnection>()
                        .eq(SysAccountChannelConnection::getProviderId, providerId)
                        .orderByAsc(SysAccountChannelConnection::getCreatedAt);
        applyOwnerFilter(wrapper);
        return connectionMapper.selectList(wrapper);
    }

    /**
     * 把接入协议同步到该渠道全部模型的能力节点。
     *
     * @param providerId 渠道标识
     * @param protocol   接入协议
     */
    private void syncProtocol(String providerId, String protocol) {
        for (SysAccountChannelModel model : modelsOf(providerId)) {
            List<java.util.Map<String, Object>> caps = model.getCapabilities();
            if (caps == null || caps.isEmpty()) {
                continue;
            }
            for (java.util.Map<String, Object> cap : caps) {
                cap.put("protocol", protocol);
            }
            modelMapper.updateByKey(model);
        }
    }

    /**
     * 给连接查询施加账号归属条件。
     *
     * <p>口径与渠道管理侧完全一致：</p>
     * <ul>
     *   <li>普通用户：只看得到自己名下的连接；</li>
     *   <li>管理员：额外可见 {@code user_id IS NULL} 的系统池（免费渠道取的就是这一批）；</li>
     *   <li>无身份（机器流量 / 后台线程）：只放系统池，绝不当作全见。</li>
     * </ul>
     *
     * @param wrapper 连接查询包装器
     */
    public void applyOwnerFilter(LambdaQueryWrapper<SysAccountChannelConnection> wrapper) {
        String owner = AccountUserContext.currentUserId();
        if (owner == null) {
            wrapper.isNull(SysAccountChannelConnection::getUserId);
            return;
        }
        boolean admin = AccountUserContext.isAdmin();
        wrapper.and(w -> {
            w.eq(SysAccountChannelConnection::getUserId, owner);
            if (admin) {
                w.or().isNull(SysAccountChannelConnection::getUserId);
            }
        });
    }

    /**
     * 必填文本校验。
     *
     * @param value 原始值
     * @param field 字段名
     * @return 去空格后的值
     */
    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "字段 " + field + " 不能为空");
        }
        return value.trim();
    }
}
