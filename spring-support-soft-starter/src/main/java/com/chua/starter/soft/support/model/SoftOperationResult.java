package com.chua.starter.soft.support.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SoftOperationResult {
    private boolean success;
    private boolean accepted;
    private boolean finished;
    private Integer exitCode;
    private Long processId;
    private String command;
    private String message;
    private String output;
    private String stdoutPath;
    private String stderrPath;
}
