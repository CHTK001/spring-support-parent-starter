package com.chua.tenant.support.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.tenant.support.entity.SysTenantService;
import com.chua.tenant.support.server.mapper.SysTenantServiceMapper;
import com.chua.tenant.support.server.service.SysTenantServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 租户服务关联实现�?
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Service
@RequiredArgsConstructor
public class SysTenantServiceServiceImpl extends ServiceImpl<SysTenantServiceMapper, SysTenantService>
        implements SysTenantServiceService {

    @Override
    public List<Integer> getMenuIds(Integer sysTenantId) {
        return baseMapper.getMenuByTenantId(sysTenantId);
    }
}
