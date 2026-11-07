package com.chua.starter.monitor.support.model;

import java.io.Serializable;

/**
 * 报文回填（-T json 通道）的参数对象。
 *
 * <p>帧号 {@code ms_tshark_packet_frame_no} 是对齐 SQLite 行（CASE WHEN）的锚点，
 * {@code rawData} 与 {@code restoredText} 一次成批回写。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public class PacketRestoreFrame implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /** pcap 内自然递增的帧号（frame.number） */
    private Long frameNo;
    /** tshark -T json 返回的单包完整 JSON */
    private String rawData;
    /** 还原出的协议明文 */
    private String restoredText;

    public PacketRestoreFrame() {
    }

    public PacketRestoreFrame(Long frameNo, String rawData, String restoredText) {
        this.frameNo = frameNo;
        this.rawData = rawData;
        this.restoredText = restoredText;
    }

    public Long getFrameNo() {
        return this.frameNo;
    }

    public void setFrameNo(Long frameNo) {
        this.frameNo = frameNo;
    }

    public String getRawData() {
        return this.rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    /** getRestoredText 方法 */
    public String getRestoredText() {
        return this.restoredText;
    }

    /** setRestoredText 方法 */
    public void setRestoredText(String restoredText) {
        this.restoredText = restoredText;
    }
}
