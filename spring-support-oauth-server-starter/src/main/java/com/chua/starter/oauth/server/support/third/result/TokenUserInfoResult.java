package com.chua.starter.oauth.server.support.third.result;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户信息
 *
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TokenUserInfoResult extends BaseResult {
    private String userid;
    private String authlevel;
    private String username;
    private String idnum;
    private String sex;
    private String nation;
    private String loginname;
    private String email;
    private String mobile;
    private String postcode;
    private String cakey;
    private String birthday;
    private String country;
    private String province;
    private String city;
    private String officeaddress;
    private String officephone;
    private String officefax;
    private String homephone;
    private String homeaddress;
    private String useable;
    private String orderby;
    private String headpicture;
}
