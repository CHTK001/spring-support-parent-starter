package com.chua.starter.oauth.server.support.third.paramter;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 票据
 *
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TicketValidationParameter extends BaseParameter {
    /**
     * 票据
     */
    private String ticket;
}
