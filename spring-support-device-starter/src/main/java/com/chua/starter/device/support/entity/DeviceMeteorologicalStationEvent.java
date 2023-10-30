package com.chua.starter.device.support.entity;

import com.influxdb.annotations.Column;
import lombok.Data;

import java.time.Instant;

/**
 * 设备气象站事件
 *
 * @author CH
 * @since 2023/10/30
 */
@Data
public class DeviceMeteorologicalStationEvent extends DeviceDataAccessEvent{

    @Column(timestamp = true)
    private Instant time;

    /**
     * 水质ph值
     */
    private Double phForWaterQuality;

    /**
     * 水质ec
     */
    private Double ecForWaterQuality;

    /**
     * 溶解氧
     */
    private Double dissolvedOxygen;

    /**
     * 浊度
     */
    private Double turbidity;

    /**
     * 降雨量
     */
    private Double rainfall;

    /**
     * 水温
     */
    private Double temperatureForWater;

    /**
     * 土壤温度
     */
    private Double temperatureForSoil;


    /**
     * 土壤湿度
     */
    private Double humidityForSoil;

    /**
     * 土壤用氮
     */
    private Double nitrogenForSoil;

    /**
     * 土壤磷
     */
    private Double phosphorusForSoil;
    /**
     * 土壤用钾
     */
    private Double potassiumForSoil;
    /**
     * 空气温度
     */
    private Double temperatureForAir;


    /**
     * 空气湿度
     */
    private Double humidityForAir;

    /**
     * 水传感器
     */
    private String waterSensor;

    /**
     * 照明强度
     */
    private Double illuminationIntensity;

    /**
     * 二氧化碳
     */
    private Double carbonDioxide;
    /**
     * 风速
     */
    private Double windSpeed;

    /**
     * 风向
     */
    private String windDirection;

    /**
     * gps
     */
    private String gps;

    /**
     * 太阳辐射
     */
    private Double solarRadiation;
    /**
     * 传感器
     */
    private String sensor;
}
