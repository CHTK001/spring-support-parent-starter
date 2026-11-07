package com.chua.starter.monitor.support.model;

import java.io.Serializable;

/**
 * 一条「TCP 重组 + HTTP 协议还原」结果，按帧号回写报文行。
 *
 * <p>回写不是覆盖成整行新值：请求头只写给请求帧、响应头只写给响应帧，
 * 其余列用 SQL 的 {@code COALESCE(新值, 旧值)} 保留 tshark 逐帧字段兜底。
 * 帧号 {@code ms_tshark_packet_frame_no} 是同一份 pcap 内两个通道的共同锚点。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public class PacketHttpFrame implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * pcap 内的自然帧号（frame.number）
     */
    private Long frameNo;

    /**
     * 协议还原出的请求头；响应帧为 null
     */
    private String requestHead;

    /**
     * 协议还原出的响应头；请求帧为 null
     */
    private String responseHead;

    /**
     * 实体正文（已解码成可读文本）
     */
    private String body;

    /**
     * 实体类型 Content-Type
     */
    private String contentType;

    /**
     * 实体编码 Content-Encoding
     */
    private String contentEncoding;

    /**
     * 实体原始字节数
     */
    private Integer contentLength;

    /**
     * 整条消息的协议还原文本（头 + 正文），回填到报文表的 restored 列
     */
    private String restoredText;

    /**
     * HTTP 请求方法；响应帧为 null
     */
    private String httpMethod;

    /**
     * HTTP 请求 URI；响应帧为 null
     */
    private String httpUri;

    /**
     * HTTP 响应状态码；请求帧为 null
     */
    private Integer httpStatus;

    /**
     * 无参构造。
     */
    public PacketHttpFrame() {
    }

    /**
     * 构造一条回写记录。
     *
     * @param frameNo         帧号
     * @param requestHead     请求头
     * @param responseHead    响应头
     * @param body            正文
     * @param contentType     类型
     * @param contentEncoding 编码
     * @param contentLength   原始字节数
     * @param restoredText    协议还原文本
     */
    public PacketHttpFrame(Long frameNo, String requestHead, String responseHead, String body,
                           String contentType, String contentEncoding, Integer contentLength,
                           String restoredText) {
        this.frameNo = frameNo;
        this.requestHead = requestHead;
        this.responseHead = responseHead;
        this.body = body;
        this.contentType = contentType;
        this.contentEncoding = contentEncoding;
        this.contentLength = contentLength;
        this.restoredText = restoredText;
    }

    /**
     * 取帧号。
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
     * 取请求头。
     *
     * @return 请求头
     */
    public String getRequestHead() {
        return this.requestHead;
    }

    /**
     * 设置请求头。
     *
     * @param requestHead 请求头
     */
    public void setRequestHead(String requestHead) {
        this.requestHead = requestHead;
    }

    /**
     * 取响应头。
     *
     * @return 响应头
     */
    public String getResponseHead() {
        return this.responseHead;
    }

    /**
     * 设置响应头。
     *
     * @param responseHead 响应头
     */
    public void setResponseHead(String responseHead) {
        this.responseHead = responseHead;
    }

    /**
     * 取正文。
     *
     * @return 正文
     */
    public String getBody() {
        return this.body;
    }

    /**
     * 设置正文。
     *
     * @param body 正文
     */
    public void setBody(String body) {
        this.body = body;
    }

    /**
     * 取内容类型。
     *
     * @return 内容类型
     */
    public String getContentType() {
        return this.contentType;
    }

    /**
     * 设置内容类型。
     *
     * @param contentType 内容类型
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * 取内容编码。
     *
     * @return 内容编码
     */
    public String getContentEncoding() {
        return this.contentEncoding;
    }

    /**
     * 设置内容编码。
     *
     * @param contentEncoding 内容编码
     */
    public void setContentEncoding(String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    /**
     * 取实体原始字节数。
     *
     * @return 字节数
     */
    public Integer getContentLength() {
        return this.contentLength;
    }

    /**
     * 设置实体原始字节数。
     *
     * @param contentLength 字节数
     */
    public void setContentLength(Integer contentLength) {
        this.contentLength = contentLength;
    }

    /**
     * 取协议还原文本。
     *
     * @return 协议还原文本
     */
    public String getRestoredText() {
        return this.restoredText;
    }

    /**
     * 设置协议还原文本。
     *
     * @param restoredText 协议还原文本
     */
    public void setRestoredText(String restoredText) {
        this.restoredText = restoredText;
    }

    /**
     * 取 HTTP 请求方法。
     *
     * @return 请求方法
     */
    public String getHttpMethod() {
        return this.httpMethod;
    }

    /**
     * 设置 HTTP 请求方法。
     *
     * @param httpMethod 请求方法
     */
    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    /**
     * 取 HTTP 请求 URI。
     *
     * @return 请求 URI
     */
    public String getHttpUri() {
        return this.httpUri;
    }

    /**
     * 设置 HTTP 请求 URI。
     *
     * @param httpUri 请求 URI
     */
    public void setHttpUri(String httpUri) {
        this.httpUri = httpUri;
    }

    /**
     * 取 HTTP 响应状态码。
     *
     * @return 状态码
     */
    public Integer getHttpStatus() {
        return this.httpStatus;
    }

    /**
     * 设置 HTTP 响应状态码。
     *
     * @param httpStatus 状态码
     */
    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }
}
