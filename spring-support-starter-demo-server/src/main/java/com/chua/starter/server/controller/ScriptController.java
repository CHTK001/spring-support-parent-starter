package com.chua.starter.server.controller;


import com.chua.starter.server.script.TDemoInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;


/**
 * <p>
 * 机构 前端控制器
 * </p>
 *
 * @author yzj
 * @since 2021-12-08
 */
@RestController
@RequestMapping("/script")
public class ScriptController {
    @Resource
    private TDemoInfo tDemoInfo;

    @GetMapping("/send")
    public String list() {
        return tDemoInfo.getId();
    }

}
