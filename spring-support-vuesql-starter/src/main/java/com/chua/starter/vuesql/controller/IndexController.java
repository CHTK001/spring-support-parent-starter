package com.chua.starter.vuesql.controller;

import com.chua.starter.common.support.result.Result;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.service.WebsqlConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 數據庫
 * @author CH
 */
@Controller
@RequestMapping("/vuesql/index")
public class IndexController {


    /**
     * 首頁
     * @return 首頁
     */
    @GetMapping
    public String index() {
        return "websql/index";
    }
}
