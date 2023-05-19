package com.chua.starter.server.user;//package com.chua.starter.server.user;
//
//import com.chua.starter.oauth.client.support.advice.def.DefSecret;
//import com.chua.starter.oauth.client.support.user.AccessSecret;
//import com.chua.starter.oauth.client.support.user.UserResult;
//import com.chua.starter.oauth.server.support.service.UserInfoService;
//import org.springframework.context.annotation.Configuration;
//
///**
// * @author CH
// */
//@Configuration
//public class SimpleUserService implements UserInfoService {
//    @Override
//    public UserResult checkLogin(String accessKey, String secretKey, String address) {
//        return new UserResult().setUsername(accessKey).setAddress(address).setAccessSecret(new AccessSecret(DefSecret.ACCESS_KEY, DefSecret.ACCESS_KEY, null));
//    }
//}
