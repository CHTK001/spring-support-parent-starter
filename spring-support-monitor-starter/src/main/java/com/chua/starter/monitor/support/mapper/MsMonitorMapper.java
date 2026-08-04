package com.chua.starter.monitor.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.mybatis.pojo.SysBase;

/**
 * 监控主表 Mapper
 *
 * @author CH
 * @since 2026/8/4
 */
public interface MsMonitorMapper extends BaseMapper<MsMonitor> {

    /**
     * 实际类型请以 SysBase 反射获取为准, 此处冗余只为复用 BaseMapper 签名
     */
    @SuppressWarnings("unused")
    default Class<? extends SysBase> baseType() {
        return MsMonitor.class;
    }
}