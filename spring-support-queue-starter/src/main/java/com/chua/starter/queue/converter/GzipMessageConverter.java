package com.chua.starter.queue.converter;

import com.chua.starter.queue.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GZIP压缩消息转换器
 * <p>
 * 对消息内容进行GZIP压缩和解压
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
@Component
public class GzipMessageConverter implements MessageConverter {

    private static final int MIN_COMPRESS_SIZE = 1024; // 1KB
    private static final String COMPRESSED_HEADER = "X-Compressed";

    @Override
    public Message convertBeforeSend(Message message) {
        byte[] payload = message.getPayload();
        if (payload == null || payload.length < MIN_COMPRESS_SIZE) {
            return message; // 小于1KB不压缩
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                gzipOut.write(payload);
            }

            byte[] compressed = baos.toByteArray();
            if (compressed.length < payload.length) {
                message.setPayload(compressed);
                message.getHeaders().put(COMPRESSED_HEADER, "gzip");
                log.debug("[Queue] 消息已压缩: destination={}, 原始大小={}, 压缩后={}, 压缩率={}%",
                    message.getDestination(), payload.length, compressed.length,
                    (100 - compressed.length * 100 / payload.length));
            }
        } catch (Exception e) {
            log.error("[Queue] 消息压缩失败: destination={}", message.getDestination(), e);
        }

        return message;
    }

    @Override
    public Message convertAfterReceive(Message message) {
        Object compressed = message.getHeaders().get(COMPRESSED_HEADER);
        if (!"gzip".equals(compressed)) {
            return message; // 未压缩的消息
        }

        try {
            byte[] payload = message.getPayload();
            ByteArrayInputStream bais = new ByteArrayInputStream(payload);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            try (GZIPInputStream gzipIn = new GZIPInputStream(bais)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = gzipIn.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
            }

            byte[] decompressed = baos.toByteArray();
            message.setPayload(decompressed);
            message.getHeaders().remove(COMPRESSED_HEADER);

            log.debug("[Queue] 消息已解压: destination={}, 压缩大小={}, 解压后={}",
                message.getDestination(), payload.length, decompressed.length);
        } catch (Exception e) {
            log.error("[Queue] 消息解压失败: destination={}", message.getDestination(), e);
        }

        return message;
    }

    @Override
    public boolean supports(Message message) {
        byte[] payload = message.getPayload();
        return payload != null && payload.length >= MIN_COMPRESS_SIZE;
    }

    @Override
    public int getOrder() {
        return 100; // 较低优先级，在加密等操作之后
    }
}
