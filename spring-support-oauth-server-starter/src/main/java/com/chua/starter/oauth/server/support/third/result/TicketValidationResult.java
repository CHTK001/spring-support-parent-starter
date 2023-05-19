package com.chua.starter.oauth.server.support.third.result;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 結果
 *
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TicketValidationResult extends BaseResult {
    private String token;
    private String userid;
    private String loginname;
    private String username;
}
