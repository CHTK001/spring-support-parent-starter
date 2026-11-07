package com.chua.starter.template.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 打印机配置实体
 *
 * <p>对应数据库表 {@code template_printer}，保存 hiprint 连接器需要的
 * 地址 / 端口 / Token 等参数；{@code template_printer_default} 为 1 的记录
 * 即应用侧的默认打印机。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
@TableName("template_printer")
public class TemplatePrinter {

    /**
     * 主键 标识
     */
    @TableId(value = "template_printer_id", type = IdType.ASSIGN_UUID)
    private String templatePrinterId;

    /**
     * 打印机设备名（业务唯一键）
     */
    @TableField("template_printer_name")
    private String templatePrinterName;

    /**
     * 连接地址（IP / 主机名）
     */
    @TableField("template_printer_address")
    private String templatePrinterAddress;

    /**
     * 连接端口
     */
    @TableField("template_printer_port")
    private Integer templatePrinterPort;

    /**
     * 连接 Token
     */
    @TableField("template_printer_token")
    private String templatePrinterToken;

    /**
     * 连接用户
     */
    @TableField("template_printer_username")
    private String templatePrinterUsername;

    /**
     * 打印机型号
     */
    @TableField("template_printer_type")
    private String templatePrinterType;

    /**
     * 备注
     */
    @TableField("template_printer_remark")
    private String templatePrinterRemark;

    /**
     * 是否默认：0 否、1 是
     */
    @TableField("template_printer_default")
    private Boolean templatePrinterDefault;

    /**
     * 创建时间
     */
    @TableField("template_printer_created_at")
    private LocalDateTime templatePrinterCreatedAt;

    /**
     * 更新时间
     */
    @TableField("template_printer_updated_at")
    private LocalDateTime templatePrinterUpdatedAt;
}
