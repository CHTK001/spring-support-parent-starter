package com.chua.report.server.starter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.report.server.starter.entity.MonitorSysGen;
import com.chua.report.server.starter.entity.MonitorSysGenTable;
import com.chua.report.server.starter.pojo.GenTable;
import com.chua.report.server.starter.pojo.TemplateResult;
import com.chua.report.server.starter.query.Download;

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

    /**
     * 更新表
     *
     * @param sysGen 系统信息
     * @param table  表
     * @return {@link Boolean}
     */
    Boolean updateTable(MonitorSysGen sysGen, GenTable table);
}
