package com.chua.starter.rust.server.ipc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.util.*;

/**
 * IPC 请求消息
 * <p>
 * Rust HTTP Server 将 HTTP 请求序列化后通过 IPC 发送给 Java。
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestMessage {

    /**
     * 请求唯一 ID
     */
    private long requestId;

    /**
     * HTTP 方法: GET, POST, PUT, DELETE 等
     */
    private String method;

    /**
     * 请求 URI (包含查询参数): /api/users?name=test
     */
    private String uri;

    /**
     * HTTP 协议版本: HTTP/1.1, HTTP/2
     */
    private String protocol;

    /**
     * 请求头 (多值)
     */
    private Map<String, List<String>> headers;

    /**
     * 请求体
     */
    private byte[] body;

    /**
     * 客户端地址
     */
    private String remoteAddr;

    /**
     * 服务端地址
     */
    private String localAddr;

    /**
     * 从 MessagePack 字节数组反序列化
     */
    public static RequestMessage fromBytes(byte[] data) throws IOException {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data)) {
            RequestMessage msg = new RequestMessage();

            int mapSize = unpacker.unpackMapHeader();
            for (int i = 0; i < mapSize; i++) {
                String key = unpacker.unpackString();
                switch (key) {
                    case "request_id" -> msg.setRequestId(unpacker.unpackLong());
                    case "method" -> msg.setMethod(unpacker.unpackString());
                    case "uri" -> msg.setUri(unpacker.unpackString());
                    case "protocol" -> msg.setProtocol(unpacker.unpackString());
                    case "headers" -> msg.setHeaders(unpackHeaders(unpacker));
                    case "body" -> {
                        if (unpacker.tryUnpackNil()) {
                            msg.setBody(null);
                        } else {
                            int len = unpacker.unpackBinaryHeader();
                            msg.setBody(unpacker.readPayload(len));
                        }
                    }
                    case "remote_addr" -> msg.setRemoteAddr(unpacker.unpackString());
                    case "local_addr" -> msg.setLocalAddr(unpacker.unpackString());
                    default -> unpacker.skipValue();
                }
            }
            return msg;
        }
    }

    /**
     * 序列化为 MessagePack 字节数组
     */
    public byte[] toBytes() throws IOException {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            packer.packMapHeader(8);

            packer.packString("request_id");
            packer.packLong(requestId);

            packer.packString("method");
            packer.packString(method);

            packer.packString("uri");
            packer.packString(uri);

            packer.packString("protocol");
            packer.packString(protocol);

            packer.packString("headers");
            packHeaders(packer, headers);

            packer.packString("body");
            if (body == null) {
                packer.packNil();
            } else {
                packer.packBinaryHeader(body.length);
                packer.writePayload(body);
            }

            packer.packString("remote_addr");
            packer.packString(remoteAddr != null ? remoteAddr : "");

            packer.packString("local_addr");
            packer.packString(localAddr != null ? localAddr : "");

            return packer.toByteArray();
        }
    }

    private static Map<String, List<String>> unpackHeaders(MessageUnpacker unpacker) throws IOException {
        int mapSize = unpacker.unpackMapHeader();
        Map<String, List<String>> headers = new LinkedHashMap<>(mapSize);
        for (int i = 0; i < mapSize; i++) {
            String key = unpacker.unpackString();
            int arrSize = unpacker.unpackArrayHeader();
            List<String> values = new ArrayList<>(arrSize);
            for (int j = 0; j < arrSize; j++) {
                values.add(unpacker.unpackString());
            }
            headers.put(key, values);
        }
        return headers;
    }

    private static void packHeaders(MessageBufferPacker packer, Map<String, List<String>> headers) throws IOException {
        if (headers == null) {
            packer.packMapHeader(0);
            return;
        }
        packer.packMapHeader(headers.size());
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            packer.packString(entry.getKey());
            List<String> values = entry.getValue();
            packer.packArrayHeader(values.size());
            for (String value : values) {
                packer.packString(value);
            }
        }
    }

    /**
     * 获取请求路径 (不含查询参数)
     */
    public String getPath() {
        if (uri == null) {
            return "/";
        }
        int queryIdx = uri.indexOf('?');
        return queryIdx > 0 ? uri.substring(0, queryIdx) : uri;
    }

    /**
     * 获取查询字符串
     */
    public String getQueryString() {
        if (uri == null) {
            return null;
        }
        int queryIdx = uri.indexOf('?');
        return queryIdx > 0 ? uri.substring(queryIdx + 1) : null;
    }

    /**
     * 获取单个请求头值
     */
    public String getHeader(String name) {
        if (headers == null) {
            return null;
        }
        List<String> values = headers.get(name);
        if (values == null || values.isEmpty()) {
            // 尝试忽略大小写
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    values = entry.getValue();
                    break;
                }
            }
        }
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }
}
