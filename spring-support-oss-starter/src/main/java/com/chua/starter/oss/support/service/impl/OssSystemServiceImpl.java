package com.chua.starter.oss.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.oss.support.mapper.OssSystemMapper;
import com.chua.starter.oss.support.pojo.OssSystem;
import com.chua.starter.oss.support.service.OssSystemService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class OssSystemServiceImpl extends ServiceImpl<OssSystemMapper, OssSystem> implements OssSystemService {

    @Override
    @Cacheable(cacheNames = "oss", key = "#bucket")
    public OssSystem getSystemByBucket(String bucket) {
        return baseMapper.selectOne(Wrappers.<OssSystem>lambdaQuery()
                .eq(OssSystem::getOssBucket, bucket)
                .last(" limit 1"));
    }
}
