package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.monitor.server.entity.MonitorSysGenTable;
import com.chua.starter.monitor.server.query.Download;
import com.chua.starter.monitor.server.result.TemplateResult;

import java.util.List;

/**
 * @author CH
 */
public interface MonitorSysGenTableService extends IService<MonitorSysGenTable> {


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
