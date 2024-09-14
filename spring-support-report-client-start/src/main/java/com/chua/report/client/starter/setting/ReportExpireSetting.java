package com.chua.report.client.starter.setting;

import com.chua.common.support.function.Upgrade;
import lombok.Data;

/**
 * 过期时间
 * @author CH
 * @since 2024/9/7
 */
@Data
public class ReportExpireSetting implements Upgrade<ReportExpireSetting> {

    /**
     * 日志
     */
    private Long log;

    /**
     * sql
     */
    private Long sql;

    /**
     * cpu
     */
    private Long cpu;

    /**
     * 设备
     */
    private Long device;
    /**
     * 内存
     */
    private Long mem;

    @Override
    public void upgrade(ReportExpireSetting reportExpireSetting) {

        if(null != reportExpireSetting.getLog()) {
            log = reportExpireSetting.getLog();
        }
        if(null != reportExpireSetting.getSql()) {
            sql = reportExpireSetting.getSql();
        }
        if(null != reportExpireSetting.getCpu()) {
            cpu = reportExpireSetting.getCpu();
        }
        if(null != reportExpireSetting.getDevice()) {
            device = reportExpireSetting.getDevice();
        }
        if(null != reportExpireSetting.getMem()) {
            mem = reportExpireSetting.getMem();
        }
    }
}
