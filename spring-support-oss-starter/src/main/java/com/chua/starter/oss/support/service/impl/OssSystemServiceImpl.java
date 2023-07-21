package com.chua.starter.oss.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.oss.support.mapper.OssSystemMapper;
import com.chua.starter.oss.support.pojo.SysOss;
import com.chua.starter.oss.support.service.OssSystemService;
import org.springframework.stereotype.Service;

@Service
public class OssSystemServiceImpl extends ServiceImpl<OssSystemMapper, SysOss> implements OssSystemService {

    @Override
    public SysOss getSystemByBucket(String bucket) {
        return baseMapper.selectOne(Wrappers.<SysOss>lambdaQuery()
                .eq(SysOss::getOssBucket, bucket));
    }
}
