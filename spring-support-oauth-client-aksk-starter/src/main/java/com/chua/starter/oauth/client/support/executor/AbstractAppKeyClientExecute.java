package com.chua.starter.oauth.client.support.executor;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.entity.ManufacturerUser;
import com.chua.starter.oauth.client.support.key.AppKey;
import lombok.RequiredArgsConstructor;

/**
 * 应用密钥客户端执行器
 *
 * @author CH
 * @since 2025/10/23 20:49
 */
@RequiredArgsConstructor
public abstract class AbstractAppKeyClientExecute implements AppKeyClientExecute{

    protected final AppKey appKey;


    @Override
    public ReturnResult<ManufacturerUser> getUserInfo(String userCode) {
        if(StringUtils.isEmpty(userCode)) {
            return ReturnResult.error("用户编码不能为空");
        }
        return parseUserInfo(userCode);
    }


    /**
     * 解析用户信息
     *
     * @param userCode 用户编码
     * @return 用户信息
     */
    abstract ReturnResult<ManufacturerUser> parseUserInfo(String userCode);

}
