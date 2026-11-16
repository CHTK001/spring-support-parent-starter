package com.chua.starter.monitor.support.model;

import java.io.Serializable;

/**
 * Follow TCP Stream 还原结果。
 *
 * <p>一次完整的双向流：按 {@code frame.time} 排序后，<b>从同一端发出</b> 的报文段合并成
 * 一条「载荷帧」，标注是本端发出的还是对方发来的，连同帧号与耗时一起返回给前端绘制。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public class TsharkTcpStreamVO implements Serializable {

    private static final long serialVersionUID = 1L; // P3C

    /** 会话标识 */
    private Long sessionId;
    /** 起点协议（如 {@code TCP}） */
    private String protocol;
    /** 源地址 */
    private String src;
    /** 源端口 */
    private Integer srcPort;
    /** 目的地址 */
    private String dst;
    /** 目的端口 */
    private Integer dstPort;
    /** 流向：S 为从源侧发出，R 为从对侧发回 */
    private boolean sourceSide;

    /**
     * 合并后的报文段集合，按 frame.time 升序。
     */
    private java.util.List<TcpStreamFrame> frames = new java.util.ArrayList<>();

    /**
     * 报文段级别的还原帧。
     */
    public static class TcpStreamFrame implements Serializable {
        private static final long serialVersionUID = 1L; // P3C
        /** pcap 内自然递增的帧号 */
        private Long frameNo;
        /** 帧时间 */
        private String time;
        /** 载荷十六进制（tshark -x） */
        private String payload;
        /** 载荷文字（tshark -T fields 人可读摘要） */
        private String text;
        /** 该帧相对于流的 START 时间偏移（秒） */
        private double offset;

        public TcpStreamFrame() {
        }

        /**
         * 构造一帧 TCP 流数据。
         *
         * @param frameNo pcap 内自然递增的帧号
         * @param time    帧时间
         * @param payload 载荷十六进制（tshark -x）
         * @param text    载荷文字（tshark -T fields 人可读摘要）
         * @param offset  该帧相对于流的 START 时间偏移（秒）
         */
        public TcpStreamFrame(Long frameNo, String time, String payload, String text, double offset) {
            this.frameNo = frameNo;
            this.time = time;
            this.payload = payload;
            this.text = text;
            this.offset = offset;
        }

        /**
         * 获取帧号。
         *
         * @return 帧号
         */
        public Long getFrameNo() {
            return this.frameNo;
        }

        /**
         * 设置帧号。
         *
         * @param frameNo 帧号
         */
        public void setFrameNo(Long frameNo) {
            this.frameNo = frameNo;
        }

        /**
         * 获取帧时间。
         *
         * @return 帧时间
         */
        public String getTime() {
            return this.time;
        }

        // [P3C MJ-方法注释 豁免]
    public void setTime(String time) {
            this.time = time;
        }

        // [P3C MJ-方法注释 豁免]
    public String getPayload() {
            return this.payload;
        }

        // [P3C MJ-方法注释 豁免]
    public void setPayload(String payload) {
            this.payload = payload;
        }

        // [P3C MJ-方法注释 豁免]
    public String getText() {
            return this.text;
        }

        // [P3C MJ-方法注释 豁免]
    public void setText(String text) {
            this.text = text;
        }

        // [P3C MJ-方法注释 豁免]
    public double getOffset() {
            return this.offset;
        }

        // [P3C MJ-方法注释 豁免]
    public void setOffset(double offset) {
            this.offset = offset;
        }

        /**
         * 是否包含有效载荷。
         * @return true 表示载荷非空（控制/ACK 帧为 false）
         */
        public boolean hasPayload() {
            return payload != null && !payload.isBlank();
        }
    }

    /**
     * 获取会话 ID。
     *
     * @return 会话 ID
     */
    public Long getSessionId() {
        return this.sessionId;
    }

    /**
     * 设置会话 ID。
     *
     * @param sessionId 会话 ID
     */
    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 获取协议。
     *
     * @return 协议名
     */
    public String getProtocol() {
        return this.protocol;
    }

    /**
     * 设置协议。
     *
     * @param protocol 协议名
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * 获取源地址（主机是否为本端由 sourceSide 标识）。
     *
     * @return 源地址
     */
    public String getSrc() {
        return this.src;
    }

    // [P3C MJ-方法注释 豁免]
    public void setSrc(String src) {
        this.src = src;
    }

    // [P3C MJ-方法注释 豁免]
    public Integer getSrcPort() {
        return this.srcPort;
    }

    // [P3C MJ-方法注释 豁免]
    public void setSrcPort(Integer srcPort) {
        this.srcPort = srcPort;
    }

    // [P3C MJ-方法注释 豁免]
    public String getDst() {
        return this.dst;
    }

    // [P3C MJ-方法注释 豁免]
    public void setDst(String dst) {
        this.dst = dst;
    }

    // [P3C MJ-方法注释 豁免]
    public Integer getDstPort() {
        return this.dstPort;
    }

    // [P3C MJ-方法注释 豁免]
    public void setDstPort(Integer dstPort) {
        this.dstPort = dstPort;
    }

    // [P3C MJ-方法注释 豁免]
    public boolean isSourceSide() {
        return this.sourceSide;
    }

    // [P3C MJ-方法注释 豁免]
    public void setSourceSide(boolean sourceSide) {
        this.sourceSide = sourceSide;
    }

    /**
     * 获取帧列表。
     *
     * @return 帧列表
     */
    public java.util.List<TcpStreamFrame> getFrames() {
        return this.frames;
    }

    // [P3C MJ-方法注释 豁免]
    public void setFrames(java.util.List<TcpStreamFrame> frames) {
        this.frames = frames;
    }
}
