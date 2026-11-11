package com.chua.starter.device.support.model;

import lombok.Data;

/**
 * 车辆续期请求。
 *
 * <p>{@code validTo} 优先；未传时按月数从「当前有效期止与当前时间取较晚者」顺延。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
public class VehicleRenewRequest {

    /**
     * 续期月数（1-120），未传 validTo 时生效
     */
    private Integer months;

    /**
     * 指定新的有效期止，yyyy-MM-dd HH:mm:ss；为空则按 months 顺延
     */
    private String validTo;
}
