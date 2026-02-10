package com.chua.starter.sync.data.support.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * SPI 类型列表响应对象
 *
 * @author CH
 * @since 2024/12/19
 */
@Data
@Schema(description = "SPI类型列表")
public class SpiTypeList implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 输入节点列表
     */
    @Schema(description = "输入节点列表")
    private List<SpiInfo> input = new ArrayList<>();

    /**
     * 输出节点列表
     */
    @Schema(description = "输出节点列表")
    private List<SpiInfo> output = new ArrayList<>();

    /**
     * 数据中心节点列表
     */
    @Schema(description = "数据中心节点列表")
    private List<SpiInfo> dataCenter = new ArrayList<>();

    /**
     * 过滤器节点列表
     */
    @Schema(description = "过滤器节点列表")
    private List<SpiInfo> filter = new ArrayList<>();
}
