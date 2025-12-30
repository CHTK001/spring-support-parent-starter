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
 * IPC 响应消息
 * <p>
 * Java 处理完请求后，将响应序列化后通过 IPC 发送给 Rust HTTP Server。
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseMessage {

    /**
     * 对应的请求 ID
     */
    private long requestId;

    /**
     * HTTP 状态码
     */
    private int status;

    /**
     * 响应头 (多值)
     */
    private Map<String, List<String>> headers;

    /**
     * 响应体
     */
    private byte[] body;

    /**
     * 从 MessagePack 字节数组反序列化
     */
    public static ResponseMessage fromBytes(byte[] data) throws IOException {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data)) {
            ResponseMessage msg = new ResponseMessage();

            int mapSize = unpacker.unpackMapHeader();
            for (int i = 0; i < mapSize; i++) {
                String key = unpacker.unpackString();
                switch (key) {
                    case "request_id" -> msg.setRequestId(unpacker.unpackLong());
                    case "status" -> msg.setStatus(unpacker.unpackInt());
                    case "headers" -> msg.setHeaders(unpackHeaders(unpacker));
                    case "body" -> {
                        if (unpacker.tryUnpackNil()) {
                            msg.setBody(null);
                        } else {
                            int len = unpacker.unpackBinaryHeader();
                            msg.setBody(unpacker.readPayload(len));
                        }
                    }
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
            packer.packMapHeader(4);

            packer.packString("request_id");
            packer.packLong(requestId);

            packer.packString("status");
            packer.packInt(status);

            packer.packString("headers");
            packHeaders(packer, headers);

            packer.packString("body");
            if (body == null) {
                packer.packNil();
            } else {
                packer.packBinaryHeader(body.length);
                packer.writePayload(body);
            }

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
     * 添加响应头
     */
    public void addHeader(String name, String value) {
        if (headers == null) {
            headers = new LinkedHashMap<>();
        }
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }

    /**
     * 设置响应头 (覆盖)
     */
    public void setHeader(String name, String value) {
        if (headers == null) {
            headers = new LinkedHashMap<>();
        }
        List<String> values = new ArrayList<>();
        values.add(value);
        headers.put(name, values);
    }
}
