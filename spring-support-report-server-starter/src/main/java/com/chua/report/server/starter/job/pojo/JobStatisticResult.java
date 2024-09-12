package com.chua.report.server.starter.job.pojo;


import lombok.Data;

@Data
public class JobStatisticResult {

    private Long[] successCount;

    private Long[] failureCount;
}
