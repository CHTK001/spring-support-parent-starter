package com.chua.starter.client.controller;


import com.chua.common.support.json.Json;
import com.chua.starter.config.annotation.ConfigValue;
import com.chua.starter.oauth.client.support.annotation.UserValue;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.server.service.DemoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * <p>
 * 机构 前端控制器
 * </p>
 *
 * @author yzj
 * @since 2021-12-08
 */
@RestController
@RequestMapping("/v1/dept")
public class DeptController {

    @Autowired(required = false)
    private DemoService demoService;

    @ConfigValue("${plugin.cors.enable}")
    private boolean core;

    @GetMapping("/list")
    public String list(@UserValue UserResume userResume) {
        AuthClientExecute authClientExecute = new AuthClientExecute();
        LoginResult accessToken = authClientExecute.logout("d9e8e672e75eb0bf74a65c96a86ccf85", LogoutType.LOGOUT_ALL);
        return Json.toJson(accessToken);
    }

}
