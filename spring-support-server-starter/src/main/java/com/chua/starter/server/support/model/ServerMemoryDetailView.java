package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内存详细信息视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerMemoryDetailView {

    private String speed;
    private Integer slotCount;
    private String formFactor;
}
