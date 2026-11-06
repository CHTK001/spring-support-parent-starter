package com.chua.starter.datasearch.service;

import com.chua.starter.datasearch.pojo.ip.IpLookupVO;

/**
 * IP 归属地数据服务。
 *
 * @author CH
 * @since 4.0.0.53
 */
public interface IpLookupDataService {

    /**
     * 查询 IP 归属地。
     *
     * @param ip IP 地址（支持 {@code 1.2.3.4:8080} 形式，端口会被忽略）
     * @return 归属地；IP 非法或数据源不可用时返回 空，由调用方给出失败码
     */
    IpLookupVO lookup(String ip);
}
