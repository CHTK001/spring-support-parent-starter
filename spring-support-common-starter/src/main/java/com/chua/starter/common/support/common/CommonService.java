package com.chua.starter.common.support.common;

import com.chua.common.support.geo.IpPosition;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.properties.CoreProperties;
import com.chua.starter.common.support.utils.RequestUtils;
import lombok.Data;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.Executor;

@Data
public class CommonService {
    private IpPosition ipPosition;

    public CommonService(CoreProperties coreProperties, Executor executorService) {
//        executorService.execute(() -> {
//            CoreProperties.Geo geo = coreProperties.getGeo();
//            this.ipPosition = IpBuilder.newBuilder().database(geo.getConfig()).build(geo.getImpl());
//        });
    }

    /**
     * 获取地址
     * @param request 请求
     * @return 结果
     */
    public String getIpPosition(HttpServletRequest request) {
        String address = RequestUtils.getIpAddress(request);
        return getIpPosition(address);
    }
    /**
     * 获取地址
     * @param address 地址
     * @return 结果
     */
    public String getIpPosition(String address) {
        if (StringUtils.isNotEmpty(address)) {
//            GeoCity city = getIpPosition().getCity(address);
//            if (LAN.equals(city.country())) {
//               return LAN;
//            }

            StringBuilder stringBuilder = new StringBuilder();
//            if (StringUtils.isNotEmpty(city.country())) {
//                stringBuilder.append(city.country()).append("/");
//            }
//            if(StringUtils.isNotEmpty(city.province())) {
//                stringBuilder.append(city.province()).append("/");
//            }
//            if (StringUtils.isNotEmpty(city.city())) {
//                stringBuilder.append(city.city());
//            }
            return stringBuilder.toString();
        }

        return null;
    }
}
