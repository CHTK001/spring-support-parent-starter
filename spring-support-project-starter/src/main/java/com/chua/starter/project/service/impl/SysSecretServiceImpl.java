package com.chua.starter.project.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.project.mapper.SysSecretMapper;
import com.chua.starter.project.service.SysSecretService;
import com.chua.starter.entity.project.SysSecret;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 *
 * @since 2024/8/30
 * @author CH    
 */
@Service
public class SysSecretServiceImpl extends ServiceImpl<SysSecretMapper, SysSecret> implements SysSecretService {

    @Override
    public IPage<SysSecret> pageForSecret(Query<SysSecret> query, SysSecret sysSecret, Set<String> roles) {
        return baseMapper.selectPage(query.createPage(), query.mpjLambda()
                .selectAll(SysSecret.class)
                .like(StringUtils.isNotBlank(sysSecret.getSysSecretGroup()), SysSecret::getSysSecretGroup, sysSecret.getSysSecretGroup())
                .like(StringUtils.isNotBlank(sysSecret.getSysSecretCode()), SysSecret::getSysSecretCode, sysSecret.getSysSecretCode())
                .like(StringUtils.isNotBlank(sysSecret.getSysSecretAppId()), SysSecret::getSysSecretAppId, sysSecret.getSysSecretAppId())
                .like(StringUtils.isNotBlank(sysSecret.getSysSecretAppSecret()), SysSecret::getSysSecretAppSecret, sysSecret.getSysSecretAppSecret())
        );
    }

    @Override
    public ReturnResult<Boolean> delete(Long sysSecretId) {
        return ReturnResult.guess(baseMapper.deleteById(sysSecretId) > 0);
    }

    @Override
    public ReturnResult<Boolean> update(Set<String> roles, SysSecret sysSecret) {
        return ReturnResult.guess(baseMapper.updateById(sysSecret) > 0);
    }

    @Override
    public ReturnResult<SysSecret> save(SysSecret sysSecret, Set<String> roles) {
        sysSecret.setSysSecretDelete(0);
        if(null == sysSecret.getSysSecretStatus()) {
            sysSecret.setSysSecretStatus(1);
        }
        baseMapper.insert(sysSecret);
        return ReturnResult.guess(sysSecret);
    }
}
