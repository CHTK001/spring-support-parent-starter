package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.entity.MsMonitorGateway;
import com.chua.starter.monitor.support.entity.MsMonitorTask;
import com.chua.starter.monitor.support.mapper.MsMonitorGatewayMapper;
import com.chua.starter.monitor.support.mapper.MsMonitorMapper;
import com.chua.starter.monitor.support.mapper.MsMonitorTaskMapper;
import com.chua.starter.monitor.support.service.MonitorService;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 监控 服务 实现
 *
 * @since 2026/8/4
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

    /**
     * 监控 映射器
    */
    private final MsMonitorMapper monitorMapper;
    /**
     * 任务 映射器, 用于级联删除
    */
    private final MsMonitorTaskMapper monitorTaskMapper;
    /**
     * 网关 映射器, 用于级联删除
    */
    private final MsMonitorGatewayMapper monitorGatewayMapper;
    /**
     * 服务器主机 映射器, 用于解析监控项归属的主机
    */
    private final MsServerHostMapper serverHostMapper;

    /**
     * 探针标识随机源（静态 最终 复用，线程安全）
    */
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitor")
    /**
     * 创建。
     * @param monitor 监控
     * @return 创建的结果
     */
    public ReturnResult<MsMonitor> create(MsMonitor monitor) {
        try {
            if (monitor.getMonitorEnabled() == null) {
                monitor.setMonitorEnabled(1);
            }
 // Agent 探针标识：未显式提供时自动生成，供 Agent 上报（/ms/监控/Agent/指标）鉴权
            if (StringUtils.isEmpty(monitor.getMonitorAgentKey())) {
                monitor.setMonitorAgentKey(generateAgentKey());
            }
            bindServerHost(monitor);
            monitorMapper.insert(monitor);
            return ReturnResult.ok(monitor);
        } catch (Exception e) {
            log.error("创建资产失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 生成探针标识（32 位十六进制，与 服务端-启动 风格一致）
     *
     * @return 探针标识
     */
    private String generateAgentKey() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitor")
    /**
     * 更新。
     * @param monitor 监控
     * @return 更新的结果
     */
    public ReturnResult<MsMonitor> update(MsMonitor monitor) {
        try {
            bindServerHost(monitor);
            monitorMapper.updateById(monitor);
            return ReturnResult.ok(monitor);
        } catch (Exception e) {
            log.error("更新资产失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true, cacheNames = "monitor")
    /**
     * 删除。
     * @param id 标识
     * @return 删除的结果
     */
    public ReturnResult<Void> delete(Long id) {
        try {
            // 级联删除关联的任务和网关
            monitorTaskMapper.delete(new LambdaQueryWrapper<MsMonitorTask>()
                    .eq(MsMonitorTask::getMonitorId, id));
            monitorGatewayMapper.delete(new LambdaQueryWrapper<MsMonitorGateway>()
                    .eq(MsMonitorGateway::getMonitorId, id));
            monitorMapper.deleteById(id);
            return ReturnResult.ok();
        } catch (Exception e) {
            log.error("删除资产失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Cacheable(cacheNames = "monitor", key = "'monitor:id:' + #id")
    /**
     * findbyid。
     * @param id 标识
     * @return findById的结果
     */
    public ReturnResult<MsMonitor> findById(Long id) {
        try {
            MsMonitor monitor = monitorMapper.selectById(id);
            return ReturnResult.ok(monitor);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    @Cacheable(cacheNames = "monitor", key = "'monitor:list:all'")
    /**
     * 列表全部。
     * @return 列表全部的结果
     */
    public ReturnResult<List<MsMonitor>> listAll() {
        try {
            return ReturnResult.ok(monitorMapper.selectList(null));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<MsMonitor>> listByServiceType(String serviceType) {
        try {
            LambdaQueryWrapper<MsMonitor> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MsMonitor::getServiceType, serviceType).eq(MsMonitor::getMonitorEnabled, 1);
            return ReturnResult.ok(monitorMapper.selectList(wrapper));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    public ReturnResult<Page<MsMonitor>> page(long pageNum, long pageSize, String keyword) {
        try {
            Page<MsMonitor> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<MsMonitor> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.isNotBlank(keyword)) {
                wrapper.like(MsMonitor::getMonitorName, keyword);
            }
            wrapper.orderByDesc(MsMonitor::getId);
            return ReturnResult.ok(monitorMapper.selectPage(page, wrapper));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    /**
     * 列表 按服务器主机。
     * @param hostId 主机标识
     * @return 列表按服务器主机的结果
     */
    public ReturnResult<List<MsMonitor>> listByServerHost(Long hostId) {
        try {
            if (hostId == null) {
                return ReturnResult.ok(Collections.emptyList());
            }
            List<MsMonitor> bound = monitorMapper.selectList(new LambdaQueryWrapper<MsMonitor>()
                    .eq(MsMonitor::getMsServerHostId, hostId));
            if (!bound.isEmpty()) {
                return ReturnResult.ok(bound);
            }
            // 存量数据从未写入绑定列，按主机地址回退匹配
            MsServerHost host = serverHostMapper.selectById(hostId);
            if (host == null || StringUtils.isBlank(host.getServerHostAddress())) {
                return ReturnResult.ok(Collections.emptyList());
            }
            return ReturnResult.ok(monitorMapper.selectList(new LambdaQueryWrapper<MsMonitor>()
                    .eq(MsMonitor::getMonitorHost, host.getServerHostAddress().trim())));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 补齐主机归属：显式绑定的主机 标识 优先，未绑定时按探针标识、再按地址解析
     *
     * @param monitor 监控实体
     */
    private void bindServerHost(MsMonitor monitor) {
        if (monitor == null || monitor.getMsServerHostId() != null) {
            return;
        }
        MsServerHost byKey = StringUtils.isBlank(monitor.getMonitorAgentKey()) ? null
                : selectHostByAgentKey(monitor.getMonitorAgentKey().trim());
        if (byKey != null) {
            monitor.setMsServerHostId(byKey.getId());
            return;
        }
        if (StringUtils.isBlank(monitor.getMonitorHost())) {
            return;
        }
        // 同地址多台主机时不猜归属，交给读取侧按地址回退匹配
        List<MsServerHost> byAddress = serverHostMapper.selectList(new LambdaQueryWrapper<MsServerHost>()
                .select(MsServerHost::getId)
                .eq(MsServerHost::getServerHostAddress, monitor.getMonitorHost().trim()));
        if (byAddress.size() == 1) {
            monitor.setMsServerHostId(byAddress.get(0).getId());
        }
    }

    /**
     * 按探针标识查主机（Agent 部署时下发的即主机侧标识，监控项录入同一值即视为同一台机器）
     *
     * @param agentKey 探针标识
     * @return 命中的主机，未命中返回 空
     */
    private MsServerHost selectHostByAgentKey(String agentKey) {
        return serverHostMapper.selectOne(new LambdaQueryWrapper<MsServerHost>()
                .select(MsServerHost::getId)
                .eq(MsServerHost::getServerHostAgentKey, agentKey)
                .last("LIMIT 1"));
    }
}
