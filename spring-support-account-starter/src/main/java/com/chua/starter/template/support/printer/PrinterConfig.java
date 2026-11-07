package com.chua.starter.template.support.printer;

import lombok.Data;

/**
 * 打印机参数（hiprint / 连接器使用）
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
public class PrinterConfig {

    /** 打印机设备名 */
    private String name;

    /** 地址 */
    private String address;

    /** 端口 */
    private Integer port;

    /** Token */
    private String token;

    /** 用户 */
    private String username;

    /** 是否为默认打印机 */
    private Boolean isDefault;

    /** 型号 */
    private String type;

    /** 备注 */
    private String remark;
}
