package com.chua.report.client.starter.entity;

import com.chua.starter.common.support.project.Project;
import lombok.Data;

/**
 * 上报数据
 * @author CH
 * @since 2024/9/12
 */
@Data
public class ReportValue<T extends ReportValue<T>>{

    public ReportValue() {
        setApplicationHost(Project.getInstance().getApplicationHost());
        setApplicationPort(Project.getInstance().getApplicationPort());
        setApplicationName(Project.getInstance().getApplicationName());
        setApplicationActive(Project.getInstance().getApplicationActive());
    }

    /**
     * 上报类型
     */
    private String reportType;
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
}
