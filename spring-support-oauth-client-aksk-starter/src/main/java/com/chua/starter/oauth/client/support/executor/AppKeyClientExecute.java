package com.chua.starter.oauth.client.support.executor;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.oauth.client.support.entity.ManufacturerUser;
import com.chua.starter.oauth.client.support.enums.Manufacturer;
import com.chua.starter.oauth.client.support.key.AppKey;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 应用密钥客户端执行器
 *
 * @author CH
 * @since 2025/10/23 20:49
 */
public interface AppKeyClientExecute {


    /**
     * 创建应用密钥客户端执行器实例
     *
     * @param manufacturer 厂商标识，用于区分不同的服务提供商，例如："aliyun"、"tencent"、"huawei"等
     * @param appKey       应用密钥对象，包含访问所需的身份认证信息
     *                     示例：new AppKey("accessKeyId", "accessKeySecret")
     * @return 返回对应厂商的AppKeyClientExecute实现类实例
     */
    static AppKeyClientExecute create(Manufacturer manufacturer, AppKey appKey) {
        return ServiceProvider.of(AppKeyClientExecute.class).getNewExtension(manufacturer, appKey);
    }

    /**
     * 获取用户信息
     *
     * @param userCode 用户授权码，用于换取用户身份信息
     *                 示例："abc123xyz"
     * @return 用户信息结果封装对象，包含ManufacturerUser实体数据
     */
    ReturnResult<ManufacturerUser> getUserInfo(String userCode);
}
