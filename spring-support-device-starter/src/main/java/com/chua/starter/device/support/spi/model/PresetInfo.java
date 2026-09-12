package com.chua.starter.device.spi.model;

import lombok.Data;

/**
* 预置位信息
*
* @since 4.0.0.42
* @author CH
 */
@Data
public class PresetInfo {

    /**
    * 预置位编号
     */
    private int presetId;

    /**
    * 预置位名称
     */
    private String name;
}