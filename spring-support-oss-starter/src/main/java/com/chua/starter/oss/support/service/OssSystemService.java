package com.chua.starter.oss.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.oss.support.pojo.SysOss;

public interface OssSystemService extends IService<SysOss> {

    /**
     * 获取数据
     *
     * @param bucket bucket
     * @return 结果
     */
    SysOss getSystemByBucket(String bucket);
}
