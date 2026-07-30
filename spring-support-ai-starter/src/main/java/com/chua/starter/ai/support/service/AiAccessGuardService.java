package com.chua.starter.ai.support.service;

public interface AiAccessGuardService {

    boolean isAppKeyEnabled();

    boolean verifyAppKey(String appKey, String appSecret, String remoteAddr);

    boolean verifyBearerToken(String auth, String remoteAddr);
}
