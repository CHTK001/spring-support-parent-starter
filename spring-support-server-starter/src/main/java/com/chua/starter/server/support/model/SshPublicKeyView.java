package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SshPublicKeyView {
    private Integer id;
    private String keyType;
    private String fingerprint;
    private String comment;
    private String content;
    private String createdAt;
}
