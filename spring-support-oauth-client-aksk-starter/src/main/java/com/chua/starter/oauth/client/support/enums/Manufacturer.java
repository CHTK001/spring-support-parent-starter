package com.chua.starter.oauth.client.support.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 厂商
 * @author CH
 * @since 2025/10/23 20:55
 */
@Getter
@RequiredArgsConstructor
public enum Manufacturer {

    INSPUR("Inspur", 0),
    ;

    private final String name;
    private final int code;
}
