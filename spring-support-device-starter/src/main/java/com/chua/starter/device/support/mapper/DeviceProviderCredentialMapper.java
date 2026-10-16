package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceProviderCredential;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供商接入凭据 映射器
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface DeviceProviderCredentialMapper extends BaseMapper<DeviceProviderCredential> {
}