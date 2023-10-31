package com.chua.starter.device.support.adaptor.factory.sennuo;

import com.chua.common.support.mapping.Mapping;
import com.chua.common.support.mapping.MappingConfig;
import com.chua.starter.device.support.adaptor.Adaptor;
import com.chua.starter.device.support.adaptor.factory.client.SnNuoClient;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * a云耀
 * @author CH
 */
public abstract class SenNuoAdaptor implements Adaptor {

    protected DeviceCloudPlatformConnector deviceCloudPlatformConnector;
    protected SnNuoClient snNuoClient;

    protected final Map<String, String> mapping = new LinkedHashMap<String, String>() {
        {

            //水质EC值, us/cm
            put("A010", "ecForWaterQuality");

            //雨量, mm
            put("A322", "rainfall");
            //水温, ℃
            put("A202", "temperatureForWater");
            put("A256", "temperatureForWater");
            //水质溶解氧, mg/L
            put("A203", "dissolvedOxygen");
            put("A257", "dissolvedOxygen");
            //电导率, us/cm
            put("A209", "specificConductance");
            //空气温度, ℃
            put("A191", "temperatureForAir");
            //水质PH, PH
            put("A211", "phForWaterQuality");
            //水质PH, PH
            put("A062", "phForWaterQuality");
            //空气湿度, %RH
            put("A192", "humidityForAir");
            //光照, Lux
            put("A270", "illuminationIntensity");
            //风速, m/s
            put("A077", "windSpeed");
            //风向,
            put("A078", "windDirection");
            //太阳辐射, W/㎡
            put("A163", "solarRadiation");
            put("A245", "solarRadiation");
            //gps,
            put("A169", "gps");
            //浊度,NTU
            put("A215", "turbidity");
            put("A375", "turbidity");
            //液位计
            put("A105", "levelInstrumentationNozzle");
        }
    };
    protected final Map<String, String> unit = new LinkedHashMap<String, String>() {
        {
            //雨量, mm
            put("rainfall", "mm");
            //空气温度, ℃
            put("temperatureForAir", " ℃");
            //水温
            put("temperatureForWater", " ℃");

            //空气湿度, %RH
            put("humidityForAir", "%RH");
            //光照, Lux
            put("illuminationIntensity", "Lux");
            //风速, m/s
            put("windSpeed", "m/s");
            //风向,
            put("windDirection", "");
            //太阳辐射, W/㎡
            put("solarRadiation", "W/㎡");
            //gps,
            put("gps", "");
            //浊度
            put("turbidity", "NTU");
            //ph
            put("phForWaterQuality", "PH");
            //电导率
            put("specificConductance", "us/cm");
            //水质EC值
            put("ecForWaterQuality", "us/cm");
            //水质溶解氧
            put("dissolvedOxygen", "mg/L");
            //液位计
            put("levelInstrumentationNozzle", "m");
        }
    };

    public SenNuoAdaptor(DeviceCloudPlatformConnector deviceCloudPlatformConnector) {
        this.deviceCloudPlatformConnector = deviceCloudPlatformConnector;
        snNuoClient =
                Mapping.of(SnNuoClient.class,
                        MappingConfig.builder()
                                .host(deviceCloudPlatformConnector.getDeviceConnectorAddress())
                                .build()).get();
    }


}
