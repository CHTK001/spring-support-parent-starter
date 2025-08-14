package com.chua.report.client.starter.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.common.mapper.EntityConverter;
import org.dizitart.no2.common.mapper.NitriteMapper;
import org.dizitart.no2.index.IndexType;
import org.dizitart.no2.repository.annotations.Entity;
import org.dizitart.no2.repository.annotations.Index;
import org.dizitart.no2.repository.annotations.Indices;

/**
 * URL日志实体类，用于记录和存储URL访问相关信息
 *
 * @author CH
 * @since 2025/8/14 19:38
 */
@Data
@Indices({
        @Index(fields = {"method", "url", "ip"}, type = IndexType.NON_UNIQUE),
        @Index(fields = {"message"}, type = IndexType.FULL_TEXT)
})
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class UrlCat implements EntityConverter<UrlCat> {

    /**
     * 请求方法
     * 例如: GET, POST, PUT, DELETE
     */
    private String method;

    /**
     * 请求的URL地址
     * 例如: /api/users, /api/products/123
     */
    private String url;

    /**
     * 客户端IP地址
     * 例如: 192.168.1.100, 2001:0db8:85a3:0000:0000:8a2e:0370:7334
     */
    private String ip;

    /**
     * 请求处理耗时(毫秒)
     * 例如: 150, 2000
     */
    private Long cost;

    /**
     * 请求相关信息或错误消息
     * 例如: "Success", "User not found", "Internal server error"
     */
    private String message;

    @Override
    public Class<UrlCat> getEntityType() {
        return UrlCat.class;
    }

    @Override
    public Document toDocument(UrlCat entity, NitriteMapper nitriteMapper) {
        return Document.createDocument()
                .put("method", entity.getMethod())
                .put("url", entity.getUrl())
                .put("ip", entity.getIp())
                .put("cost", entity.getCost())
                .put("message", entity.getMessage())
                ;
    }

    @Override
    public UrlCat fromDocument(Document document, NitriteMapper nitriteMapper) {
        UrlCat urlCat = new UrlCat(
                document.get("method", String.class),
                document.get("url", String.class),
                document.get("ip", String.class),
                document.get("cost", Long.class),
                document.get("message", String.class)
        );
        return urlCat;
    }
}
