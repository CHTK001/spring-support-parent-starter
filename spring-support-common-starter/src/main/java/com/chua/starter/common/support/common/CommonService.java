package com.chua.starter.common.support.common;

import com.chua.common.support.geo.IpBuilder;
import com.chua.common.support.geo.IpPosition;
import com.chua.starter.common.support.properties.CoreProperties;
import lombok.Data;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@Data
public class CommonService {
    private IpPosition ipPosition;

    public CommonService(CoreProperties coreProperties, Executor executorService) {
        executorService.execute(() -> {
            CoreProperties.Geo geo = coreProperties.getGeo();
            this.ipPosition = IpBuilder.newBuilder().database(geo.getConfig()).build(geo.getImpl());
        });
    }
}
