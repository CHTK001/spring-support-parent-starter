package com.chua.starter.gen.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.gen.support.entity.SysGenTable;

/**
 * @author CH
 */
public interface SysGenTableService extends IService<SysGenTable> {


    /**
     * 下载代码
     *
     * @param tabIds 选项卡ID
     * @return {@link byte[]}
     */
    byte[] downloadCode(String tabIds);
}
