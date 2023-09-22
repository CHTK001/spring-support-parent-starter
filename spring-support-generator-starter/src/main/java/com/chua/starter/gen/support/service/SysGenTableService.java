package com.chua.starter.gen.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.gen.support.entity.SysGenTable;
import com.chua.starter.gen.support.query.Download;

/**
 * @author CH
 */
public interface SysGenTableService extends IService<SysGenTable> {


    /**
     * 下载代码
     *
     * @param download 下载
     * @return {@link byte[]}
     */
    byte[] downloadCode(Download download);
}
