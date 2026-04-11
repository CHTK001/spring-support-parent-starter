package com.chua.starter.server.support.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServerProcessRealtimePayload {

    private Integer serverId;

    private String serverCode;

    private String keyword;

    private Integer limit;

    private Integer processCount;

    private Long refreshedAt;

    private String message;

    private String executionProvider;

    private String spiChannel;

    private List<ServerProcessView> processes;
}
