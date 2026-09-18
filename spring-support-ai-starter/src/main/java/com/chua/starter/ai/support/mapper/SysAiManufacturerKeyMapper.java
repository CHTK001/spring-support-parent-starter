package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiManufacturerKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* AI 厂商 API 钥匙 映射器。
*
* @since 4.0.0.42
* @author CH
 */
@Mapper
public interface SysAiManufacturerKeyMapper extends BaseMapper<SysAiManufacturerKey> {

    /**
    * 按厂商编码查询 API 钥匙 列表。
    *
    * @param manufacturerCode 厂商编码
    * @return API 钥匙 列表
    * @param @Param("manufacturerCode" @参数("manufacturer编码"
    */
    List<SysAiManufacturerKey> selectByManufacturerCode(@Param("manufacturerCode") String manufacturerCode);
}
