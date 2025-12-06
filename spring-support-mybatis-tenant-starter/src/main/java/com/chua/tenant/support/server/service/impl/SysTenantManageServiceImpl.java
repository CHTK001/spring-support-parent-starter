package com.chua.tenant.support.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.constant.Action;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.mybatis.entity.Query;
import com.chua.tenant.support.entity.SysTenant;
import com.chua.tenant.support.entity.SysTenantService;
import com.chua.tenant.support.server.mapper.SysTenantMapper;
import com.chua.tenant.support.pojo.SyncTenantData;
import com.chua.tenant.support.pojo.SyncTenantServiceData;
import com.chua.tenant.support.pojo.SysTenantServiceBindV1Request;
import com.chua.tenant.support.pojo.SysTenantSyncV1Request;
import com.chua.tenant.support.server.service.SysTenantManageService;
import com.chua.tenant.support.server.service.SysTenantServiceService;
import com.chua.tenant.support.server.service.TenantMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 租户管理服务实现�?
 * <p>
 * 提供租户的增删改查、服务绑定、数据同步等功能
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Slf4j
@Service("sysTenantManageService")
@RequiredArgsConstructor
public class SysTenantManageServiceImpl extends ServiceImpl<SysTenantMapper, SysTenant>
        implements SysTenantManageService {

    private final TransactionTemplate transactionTemplate;
    private final SysTenantServiceService sysTenantServiceService;

    @Autowired(required = false)
    private TenantMessagePublisher tenantMessagePublisher;

    @Override
    public IPage<SysTenant> pageForTenant(Query<SysTenant> query, SysTenant sysTenant) {
        Page<SysTenant> page = page(query.createPage(),
                Wrappers.<SysTenant>lambdaQuery()
                        .likeRight(StringUtils.isNotBlank(sysTenant.getSysTenantName()),
                                SysTenant::getSysTenantName, sysTenant.getSysTenantName())
                        .likeRight(StringUtils.isNotBlank(sysTenant.getSysTenantCode()),
                                SysTenant::getSysTenantCode, sysTenant.getSysTenantCode())
                        .orderByDesc(SysTenant::getCreateTime)
        );

        List<SysTenant> records = page.getRecords();
        if (CollectionUtils.isNotEmpty(records)) {
            List<Integer> tenantIds = records.stream().map(SysTenant::getSysTenantId).toList();
            List<SysTenantService> list = sysTenantServiceService
                    .list(Wrappers.<SysTenantService>lambdaQuery()
                            .in(SysTenantService::getSysTenantId, tenantIds));
            Map<Integer, List<SysTenantService>> collect = list.stream()
                    .collect(Collectors.groupingBy(SysTenantService::getSysTenantId));
            for (SysTenant record : records) {
                List<SysTenantService> services = collect.getOrDefault(record.getSysTenantId(), List.of());
                record.setSysTenantService(services);
                record.setServiceIds(services.stream().map(SysTenantService::getSysServiceId).toList());
            }
        }
        return page;
    }

    @Override
    public ReturnResult<SysTenant> saveForTenant(SysTenant sysTenant) {
        if (exists(Wrappers.<SysTenant>lambdaQuery()
                .eq(SysTenant::getSysTenantCode, sysTenant.getSysTenantCode()))) {
            return ReturnResult.error("租户编码已存�?);
        }
        sysTenant.setSysTenantDelete(0);
        sysTenant.setSysTenantStatus(0);
        return transactionTemplate.execute(it -> {
            boolean saved = save(sysTenant);
            if (saved) {
                bindTenantService(new SysTenantServiceBindV1Request(
                        sysTenant.getSysTenantId(), sysTenant.getSysTenantService()));
                publish("tenant", new SyncTenantData(Action.CREATE, sysTenant));
            }
            return ReturnResult.preconditioning(sysTenant).toResult();
        });
    }

    @Override
    public ReturnResult<Boolean> updateForTenant(SysTenant sysTenant) {
        if (exists(Wrappers.<SysTenant>lambdaQuery()
                .eq(SysTenant::getSysTenantCode, sysTenant.getSysTenantCode())
                .ne(SysTenant::getSysTenantId, sysTenant.getSysTenantId()))) {
            return ReturnResult.error("租户编码已存�?);
        }
        sysTenant.setSysTenantDelete(null);
        return transactionTemplate.execute(it -> {
            bindTenantService(new SysTenantServiceBindV1Request(
                    sysTenant.getSysTenantId(), sysTenant.getSysTenantService()));
            boolean updated = updateById(sysTenant);
            if (updated) {
                publish("tenant", new SyncTenantData(Action.UPDATE, sysTenant));
            }
            return ReturnResult.preconditioning(updated).toResult();
        });
    }

    @Override
    public ReturnResult<Boolean> deleteForTenant(Long id) {
        SysTenant sysTenant = getById(id);
        if (null == sysTenant) {
            return ReturnResult.illegal("数据不存�?);
        }
        boolean removed = removeById(id);
        if (removed) {
            publish("tenant", new SyncTenantData(Action.DELETE, sysTenant));
        }
        return ReturnResult.preconditioning(removed).toResult();
    }

    @Override
    public ReturnResult<Boolean> bindTenantService(SysTenantServiceBindV1Request request) {
        return transactionTemplate.execute(status -> {
            if (CollectionUtils.isNotEmpty(request.getSysServiceIds())) {
                SysTenant sysTenant = baseMapper.selectById(request.getSysTenantId());
                // 删除原有绑定
                sysTenantServiceService.remove(Wrappers.<SysTenantService>lambdaQuery()
                        .eq(SysTenantService::getSysTenantId, request.getSysTenantId()));
                // 创建新绑�?
                List<SysTenantService> list = request.getSysServiceIds().stream()
                        .map(item -> {
                            SysTenantService service = new SysTenantService();
                            service.setSysTenantId(request.getSysTenantId());
                            service.setSysServiceId(item.getSysServiceId());
                            service.setSysTenantServiceValidTime(item.getSysTenantServiceValidTime());
                            return service;
                        }).toList();
                sysTenantServiceService.saveBatch(list);

                // 获取菜单并广�?
                List<Integer> menuIds = sysTenantServiceService.getMenuIds(request.getSysTenantId());
                publish("tenant", new SyncTenantData(Action.UPDATE, sysTenant));
                publish("service", new SyncTenantServiceData(Action.UPDATE, request.getSysTenantId(), menuIds));
            }
            return ReturnResult.ok();
        });
    }

    @Override
    public ReturnResult<Boolean> syncTenantData(SysTenantSyncV1Request request) {
        return transactionTemplate.execute(status -> {
            Integer sysTenantId = request.getSysTenantId();
            SysTenant sysTenant = baseMapper.selectById(sysTenantId);
            List<Integer> menuIds = ObjectUtils.equal(sysTenant.getSysTenantStatus(), 0)
                    ? sysTenantServiceService.getMenuIds(sysTenantId)
                    : Collections.emptyList();

            publish("tenant", new SyncTenantData(Action.UPDATE, sysTenant));
            publish("service", new SyncTenantServiceData(Action.UPDATE, request.getSysTenantId(), menuIds));
            return ReturnResult.ok();
        });
    }

    /**
     * 发布消息
     *
     * @param topic 主题
     * @param data  数据
     */
    private void publish(String topic, Object data) {
        if (tenantMessagePublisher != null) {
            tenantMessagePublisher.publish(topic, data);
        } else {
            log.debug("[Tenant] TenantMessagePublisher 未配置，跳过数据同步");
        }
    }
}
