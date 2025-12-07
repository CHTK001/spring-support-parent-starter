package com.chua.tenant.support.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.mybatis.entity.Query;
import com.chua.tenant.support.common.entity.SysService;
import com.chua.tenant.support.common.entity.SysServiceModuleItem;
import com.chua.tenant.support.server.mapper.SysServiceMapper;
import com.chua.tenant.support.server.pojo.SysServiceBindV1Request;
import com.chua.tenant.support.server.service.SysServiceModuleItemService;
import com.chua.tenant.support.server.service.SysServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 服务服务实现类
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysServiceServiceImpl extends ServiceImpl<SysServiceMapper, SysService>
        implements SysServiceService {

    private final TransactionTemplate transactionTemplate;
    private final SysServiceModuleItemService sysServiceModuleItemService;

    @Override
    public IPage<SysService> pageForSysService(Query<SysService> query, SysService sysService) {
        log.debug("分页查询服务, 条件: {}", sysService);
        Page<SysService> sysServicePage = baseMapper.selectPage(query.createPage(),
                Wrappers.<SysService>lambdaQuery()
                        .likeRight(StringUtils.isNotBlank(sysService.getSysServiceName()),
                                SysService::getSysServiceName, sysService.getSysServiceName())
                        .likeRight(StringUtils.isNotBlank(sysService.getSysServiceCode()),
                                SysService::getSysServiceCode, sysService.getSysServiceCode())
                        .orderByAsc(SysService::getSysServiceSort)
        );

        List<SysService> records = sysServicePage.getRecords();
        if (CollectionUtils.isNotEmpty(records)) {
            // 填充关联的服务模块标签
            fillServiceTags(records);
        }
        return sysServicePage;
    }

    @Override
    public ReturnResult<SysService> saveForSysService(SysService sysService) {
        log.debug("保存服务: {}", sysService);
        if (exists(Wrappers.<SysService>lambdaQuery()
                .eq(SysService::getSysServiceCode, sysService.getSysServiceCode()))) {
            log.warn("服务编码已存在: {}", sysService.getSysServiceCode());
            return ReturnResult.error("服务编码已存在");
        }
        sysService.setSysServiceDelete(0);
        sysService.setSysServiceStatus(0);
        save(sysService);
        log.info("服务保存成功, ID: {}", sysService.getSysServiceId());
        return ReturnResult.success(sysService);
    }

    @Override
    public ReturnResult<Boolean> updateForSysService(SysService sysService) {
        log.debug("更新服务: {}", sysService);
        if (exists(Wrappers.<SysService>lambdaQuery()
                .eq(SysService::getSysServiceCode, sysService.getSysServiceCode())
                .ne(SysService::getSysServiceId, sysService.getSysServiceId()))) {
            log.warn("服务编码已存在: {}", sysService.getSysServiceCode());
            return ReturnResult.error("服务编码已存在");
        }
        sysService.setSysServiceDelete(null);
        boolean result = updateById(sysService);
        log.info("服务更新结果: {}", result);
        return ReturnResult.preconditioning(result).toResult();
    }

    @Override
    public ReturnResult<Boolean> deleteForSysService(Long sysServiceId) {
        log.debug("删除服务, ID: {}", sysServiceId);
        boolean result = removeById(sysServiceId);
        log.info("服务删除结果: {}", result);
        return ReturnResult.preconditioning(result).toResult();
    }

    @Override
    public ReturnResult<Boolean> bindForSysService(SysServiceBindV1Request request) {
        log.debug("绑定服务模块, 服务ID: {}, 模块IDs: {}", request.getSysServiceId(), request.getSysServiceModuleIds());
        return transactionTemplate.execute(status -> {
            if (CollectionUtils.isNotEmpty(request.getSysServiceModuleIds())) {
                // 先删除原有关联
                sysServiceModuleItemService.remove(Wrappers.<SysServiceModuleItem>lambdaQuery()
                        .eq(SysServiceModuleItem::getSysServiceId, request.getSysServiceId()));
                // 保存新的关联
                sysServiceModuleItemService.saveBatch(request.getSysServiceModuleIds().stream().map(sysServiceModuleId -> {
                    SysServiceModuleItem sysServiceModuleItem = new SysServiceModuleItem();
                    sysServiceModuleItem.setSysServiceId(request.getSysServiceId());
                    sysServiceModuleItem.setSysServiceModuleId(sysServiceModuleId);
                    return sysServiceModuleItem;
                }).toList());
                log.info("服务模块绑定成功");
            }
            return ReturnResult.ok();
        });
    }

    @Override
    public ReturnResult<List<SysService>> listForSysService(SysService sysService) {
        log.debug("查询服务列表, 条件: {}", sysService);
        List<SysService> records = baseMapper.selectList(
                Wrappers.<SysService>lambdaQuery()
                        .likeRight(StringUtils.isNotBlank(sysService.getSysServiceName()),
                                SysService::getSysServiceName, sysService.getSysServiceName())
                        .likeRight(StringUtils.isNotBlank(sysService.getSysServiceCode()),
                                SysService::getSysServiceCode, sysService.getSysServiceCode())
                        .orderByAsc(SysService::getSysServiceSort)
        );

        if (CollectionUtils.isNotEmpty(records)) {
            // 填充关联的服务模块标签
            fillServiceTags(records);
        }
        return ReturnResult.ok(records);
    }

    /**
     * 填充服务的关联模块标签
     *
     * @param records 服务列表
     */
    private void fillServiceTags(List<SysService> records) {
        List<SysServiceModuleItem> list = sysServiceModuleItemService.list(
                Wrappers.<SysServiceModuleItem>lambdaQuery()
                        .in(SysServiceModuleItem::getSysServiceId,
                                records.stream().map(SysService::getSysServiceId).toList()));
        Map<Integer, List<SysServiceModuleItem>> collect = list.stream()
                .collect(Collectors.groupingBy(SysServiceModuleItem::getSysServiceId));
        for (SysService record : records) {
            record.setSysServiceTags(collect.getOrDefault(record.getSysServiceId(), List.of())
                    .stream().map(SysServiceModuleItem::getSysServiceModuleId).toList());
        }
    }
}
