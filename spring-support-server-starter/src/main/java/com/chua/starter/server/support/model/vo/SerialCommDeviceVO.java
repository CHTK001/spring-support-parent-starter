package com.chua.starter.server.support.model.vo;

/**
 * 本机串口设备列表项
 * <p>
 * 来自 jSerialComm 的系统设备枚举；列出设备路径、标识与占用状态，
 * 供管理端与主机台账（串口设备）使用。
 * </p>
 *
 * @author CH
 * @since 4.0.0.48
 */
public class SerialCommDeviceVO {

    /**
     * 系统端口名（Windows 上如 COM3，Unix 上如 /dev/ttyUSB0）
     */
    private String name;

    /**
     * 系统路径（设备节点）
     */
    private String path;

    /**
     * 设备描述（厂商/设备名等）
     */
    private String description;

    /**
     * 厂商 ID
     */
    private int vendorId;

    /**
     * 产品 ID
     */
    private int productId;

    /**
     * 是否已被独占打开（不可再用）
     */
    private boolean opened;

    /**
     * 是否在本机设备列表中找到（可被打开的前提）
     */
    private boolean available;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getVendorId() {
        return vendorId;
    }

    public void setVendorId(int vendorId) {
        this.vendorId = vendorId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public boolean isOpened() {
        return opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
