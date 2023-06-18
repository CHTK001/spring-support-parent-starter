package com.chua.starter.oss.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.oss.support.pojo.OssSystem;

public interface OssSystemService extends IService<OssSystem> {

    /**
     * 获取数据
     *
     * @param bucket bucket
     * @return 结果
     */
    OssSystem getSystemByBucket(String bucket);
}
