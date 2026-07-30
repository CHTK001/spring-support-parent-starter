package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerFileWatchTicket {
    private Long watchId;
    private Integer serverId;
    private String path;
    private Long acceptedAt;
}
