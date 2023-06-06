package com.chua.starter.common.support.common;

import com.chua.common.support.geo.IpBuilder;
import com.chua.common.support.geo.IpPosition;
import com.chua.starter.common.support.properties.CoreProperties;
import lombok.Data;

@Data
public class CommonService {
    private IpPosition ipPosition;

    public CommonService(CoreProperties coreProperties) {
        CoreProperties.Geo geo = coreProperties.getGeo();
        this.ipPosition = IpBuilder.newBuilder().database(geo.getConfig()).build(geo.getImpl());
    }
}
