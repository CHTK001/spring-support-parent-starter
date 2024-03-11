package com.chua.starter.monitor.server.job.pojo;


import lombok.Data;

@Data
public class JobStatisticResult {

    private Long[] successCount;

    private Long[] failureCount;
}
