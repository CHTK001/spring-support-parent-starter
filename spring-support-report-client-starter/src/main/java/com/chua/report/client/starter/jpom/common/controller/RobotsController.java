/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.common.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URL;

/**
 * robots 接口
 *
 * @author bwcx_jzy
 * @since 2022/3/5
 */
@RestController
public class RobotsController {

    @GetMapping(value = "robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public void robots(HttpServletResponse response) {
        URL resource = ResourceUtil.getResource("robots.txt");
        String readString = FileUtil.readString(resource, CharsetUtil.CHARSET_UTF_8);
        JakartaServletUtil.write(response, readString, MediaType.TEXT_PLAIN_VALUE);
    }
}
