package com.chua.starter.oss.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.common.support.configuration.CacheConfiguration;
import com.chua.starter.oss.support.mapper.OssSystemMapper;
import com.chua.starter.oss.support.pojo.SysOss;
import com.chua.starter.oss.support.service.OssSystemService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class OssSystemServiceImpl extends ServiceImpl<OssSystemMapper, SysOss> implements OssSystemService {

    @Override
    @Cacheable(cacheManager = CacheConfiguration.DEFAULT_CACHE_MANAGER, cacheNames = "oss", key = "#bucket")
    public SysOss getSystemByBucket(String bucket) {
        return baseMapper.selectOne(Wrappers.<SysOss>lambdaQuery()
                .eq(SysOss::getOssBucket, bucket)
                .last(" limit 1"));
    }
}
