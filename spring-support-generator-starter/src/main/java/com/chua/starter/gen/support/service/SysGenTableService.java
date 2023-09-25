package com.chua.starter.gen.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.gen.support.entity.SysGenTable;
import com.chua.starter.gen.support.query.Download;
import com.chua.starter.gen.support.result.TemplateResult;

import java.util.List;

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

    /**
     * 样板
     *
     * @param tabId 选项卡id
     * @return {@link List}<{@link String}>
     */
    List<TemplateResult> template(Integer tabId);
}
