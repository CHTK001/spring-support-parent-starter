package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerProcessCommandResult {

    private Integer serverId;
    private Long pid;
    private Boolean success;
    private Integer exitCode;
    private Boolean force;
    private String message;
    private String output;
}
