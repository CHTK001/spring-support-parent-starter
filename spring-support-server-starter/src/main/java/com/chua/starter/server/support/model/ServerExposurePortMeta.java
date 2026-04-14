package com.chua.starter.server.support.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerExposurePortMeta {
    private Integer total;
    private List<String> protocols;
    private List<String> states;
    private List<String> processNames;
}
