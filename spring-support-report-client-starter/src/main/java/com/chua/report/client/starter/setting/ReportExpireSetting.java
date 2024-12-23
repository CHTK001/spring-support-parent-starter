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
     * 日志(s)
     */
    private Long log =  604800L;
    /**
     * trace(s)
     */
    private Long trace =  604800L;

    /**
     * sql(s)
     */
    private Long sql =  604800L;

    /**
     * url(s)
     */
    private Long url =  604800L;
    /**
     * cpu(s)
     */
    private Long cpu =  604800L;

    /**
     * 设备(s)
     */
    private Long device =  604800L;
    /**
     * 内存(s)
     */
    private Long mem =  604800L;
    /**
     * 网络(s)
     */
    private Long network =  604800L;

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
