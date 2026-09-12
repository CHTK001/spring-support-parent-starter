package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主机采集上下文
 * <p>
   * 封装一次采集所需的全部入参（采集方式 + 目标主机），由调度器构造并传入 提供者。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostCollectContext {

    /**
     * 采集方式
     */
    private HostCollectType mode;

    /**
     * 目标主机
     */
    private MsServerHost host;
}
