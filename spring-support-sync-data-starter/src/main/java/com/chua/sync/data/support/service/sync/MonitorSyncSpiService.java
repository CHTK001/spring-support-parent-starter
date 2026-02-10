package com.chua.sync.data.support.service.sync;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.sync.data.support.sync.SpiInfo;
import com.chua.sync.data.support.sync.SpiParameter;
import com.chua.sync.data.support.sync.SpiTypeList;

import java.util.List;
import java.util.Map;

/**
 * 同步 SPI 服务接口
 * <p>
 * 用于获取 Input/Output/DataCenter/DataFilter 等 SPI 的信息和参数
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 */
public interface MonitorSyncSpiService {

    /**
     * 获取所有 Input SPI 列表
     *
     * @return SPI 列表
     */
    ReturnResult<List<SpiInfo>> getInputList();

    /**
     * 获取所有 Output SPI 列表
     *
     * @return SPI 列表
     */
    ReturnResult<List<SpiInfo>> getOutputList();

    /**
     * 获取所有 DataCenter SPI 列表
     *
     * @return SPI 列表
     */
    ReturnResult<List<SpiInfo>> getDataCenterList();

    /**
     * 获取所有 DataFilter SPI 列表
     *
     * @return SPI 列表
     */
    ReturnResult<List<SpiInfo>> getDataFilterList();

    /**
     * 获取指定 SPI 类型和名称的配置参数定义
     *
     * @param spiType SPI 类型: INPUT/OUTPUT/DATA_CENTER/FILTER
     * @param spiName SPI 名称
     * @return 参数定义列表
     */
    ReturnResult<List<SpiParameter>> getParameters(String spiType, String spiName);

    /**
     * 验证 SPI 配置
     *
     * @param spiType SPI 类型
     * @param spiName SPI 名称
     * @param config 配置参数
     * @return 验证结果
     */
    ReturnResult<Boolean> validateConfig(String spiType, String spiName, Map<String, Object> config);

    /**
     * 测试 SPI 连接(如数据库连接测试)
     *
     * @param spiType SPI 类型
     * @param spiName SPI 名称
     * @param config 配置参数
     * @return 测试结果
     */
    ReturnResult<String> testConnection(String spiType, String spiName, Map<String, Object> config);

    /**
     * 获取所有 SPI 类型信息(用于前端显示)
     *
     * @return SPI类型列表对象
     */
    ReturnResult<SpiTypeList> getAllSpiTypes();
}
