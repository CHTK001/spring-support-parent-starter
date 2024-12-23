package com.chua.report.client.starter.report.event;

import com.chua.common.support.utils.DigestUtils;
import com.chua.starter.common.support.project.Project;
import lombok.Data;
import lombok.Getter;

/**
 * 上报数据
 *
 * @author CH
 * @since 2024/9/12
 */
@Data
public class ReportEvent<T> {

    public ReportEvent() {
        setApplicationHost(Project.getInstance().getApplicationHost());
        setApplicationPort(Project.getInstance().getApplicationPort());
        setApplicationName(Project.getInstance().getApplicationName());
        setApplicationActive(Project.getInstance().getApplicationActive());
    }

    /**
     * 上报类型
     */
    private ReportType reportType;
    /**
     * 应用名称
     */
    private String applicationName;

    /**
     * 应用端口
     */
    private Integer applicationPort;

    /**
     * 应用地址
     */
    private String applicationHost;

    /**
     * 应用环境
     */
    private String applicationActive;

    /**
     * 上报时间
     */
    private long timestamp = System.currentTimeMillis();
    /**
     * 上报数据
     */
    private T reportData;

    /**
     * 计算事件ID
     *
     * @return
     */
    public String[] eventIds() {
        return new String[]{
                reportType.name(),
                clientEventId()
        };
    }

    /**
     * 计算事件ID
     *
     * @return
     */
    public String clientEventId() {
        return DigestUtils.md5Hex(reportType.name() + ":" + applicationHost + applicationPort);
    }


    @Getter
    public static enum ReportType {

        /**
         * 进程
         */
        //PROCESS,
        /**
         * 日志
         */
        LOG,

        /**
         * sql
         */
        SQL,

        /**
         * url
         */
        URL,
        /**
         * jvm
         */
        JVM,

        /**
         * cpu
         */
        CPU,

        /**
         * 磁盘
         */
        DISK,

        /**
         * usb
         */
        //USB,
        /**
         * 内存
         */
        MEM,
        /**
         * 服务
         */
        SERVER,
        /**
         * 系统
         */
        SYS,

        /**
         * 网络
         */
       // NETWORK,

        /**
         * 网络状态
         */
       // NETSTAT,
        /**
         * trace
         */
        TRACE,
        /**
         * 磁盘io
         */
//        IO_DISK,

        /**
         * 网络io
         */
        IO_NETWORK,
        /**
         * 全部
         */
        ALL
    }
}
