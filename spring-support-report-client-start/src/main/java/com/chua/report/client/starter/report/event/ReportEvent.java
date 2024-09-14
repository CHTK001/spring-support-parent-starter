package com.chua.report.client.starter.report.event;

import com.chua.starter.common.support.project.Project;
import lombok.Data;
import lombok.Getter;

/**
 * 上报数据
 * @author CH
 * @since 2024/9/12
 */
@Data
public class ReportEvent<T>{

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
     * 上报数据
     */
    private T reportData;

    /**
     * 计算事件ID
     * @return
     */
    public String[] calcEventIds() {
        return new String[]{
                reportType.name(),
                reportType.name() +":" + applicationHost + applicationPort
        };
    }


    @Getter
    public static enum ReportType {

        /**
         * 日志
         */
        LOG,

        /**
         * sql
         */
        SQL,


        /**
         * cpu
         */
        CPU,

        /**
         * 内存
         */
        MEM,
        /**
         * agent日志
         */
        AGENT_LOG,

        /**
         * agent sql
         */
        AGENT_SQL,
        /**
         * agent trace
         */
        AGENT_TRANCE
    }
}
