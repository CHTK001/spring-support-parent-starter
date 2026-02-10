package com.chua.starter.sync.data.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.sync.data.support.service.sync.MonitorSyncSpiService;
import com.chua.starter.sync.data.support.sync.SpiInfo;
import com.chua.starter.sync.data.support.sync.SpiParameter;
import com.chua.starter.sync.data.support.sync.SpiTypeList;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 同步 SPI 管理 Controller
 *
 * @author CH
 * @since 2024/12/19
 */
@RestController
@RequestMapping("/v1/sync/spi")
@Tag(name = "同步SPI管理")
@RequiredArgsConstructor
public class MonitorSyncSpiController {

    private final MonitorSyncSpiService spiService;

    /**
     * 获取所有 SPI 类型列表
     */
    @GetMapping("/all")
    @Operation(summary = "获取所有SPI类型列表")
    public ReturnResult<SpiTypeList> getAllSpiTypes() {
        return spiService.getAllSpiTypes();
    }

    /**
     * 获取 Input SPI 列表
     */
    @GetMapping("/input")
    @Operation(summary = "获取Input SPI列表")
    public ReturnResult<List<SpiInfo>> getInputList() {
        return spiService.getInputList();
    }

    /**
     * 获取 Output SPI 列表
     */
    @GetMapping("/output")
    @Operation(summary = "获取Output SPI列表")
    public ReturnResult<List<SpiInfo>> getOutputList() {
        return spiService.getOutputList();
    }

    /**
     * 获取 DataCenter SPI 列表
     */
    @GetMapping("/datacenter")
    @Operation(summary = "获取DataCenter SPI列表")
    public ReturnResult<List<SpiInfo>> getDataCenterList() {
        return spiService.getDataCenterList();
    }

    /**
     * 获取 Filter SPI 列表
     */
    @GetMapping("/filter")
    @Operation(summary = "获取Filter SPI列表")
    public ReturnResult<List<SpiInfo>> getFilterList() {
        return spiService.getDataFilterList();
    }

    /**
     * 获取 SPI 参数定义
     */
    @GetMapping("/parameters")
    @Operation(summary = "获取SPI参数定义")
    public ReturnResult<List<SpiParameter>> getParameters(
            @Parameter(description = "SPI类型: INPUT/OUTPUT/DATA_CENTER/FILTER") @RequestParam String spiType,
            @Parameter(description = "SPI名称") @RequestParam String spiName) {
        return spiService.getParameters(spiType, spiName);
    }

    /**
     * 验证 SPI 配置
     */
    @PostMapping("/validate")
    @Operation(summary = "验证SPI配置")
    public ReturnResult<Boolean> validateConfig(
            @Parameter(description = "SPI类型") @RequestParam String spiType,
            @Parameter(description = "SPI名称") @RequestParam String spiName,
            @RequestBody Map<String, Object> config) {
        return spiService.validateConfig(spiType, spiName, config);
    }

    /**
     * 测试 SPI 连接
     */
    @PostMapping("/test")
    @Operation(summary = "测试SPI连接")
    public ReturnResult<String> testConnection(
            @Parameter(description = "SPI类型") @RequestParam String spiType,
            @Parameter(description = "SPI名称") @RequestParam String spiName,
            @RequestBody Map<String, Object> config) {
        return spiService.testConnection(spiType, spiName, config);
    }
}
