package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.SystemLogEntry;
import com.chua.starter.server.support.model.SystemLogSource;
import com.chua.starter.server.support.service.ServerLogViewService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/server/hosts/{id}/log-view")
public class ServerLogViewController {

    private final ServerLogViewService serverLogViewService;

    @GetMapping("/sources")
    public ReturnResult<List<SystemLogSource>> listSources(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(serverLogViewService.listSources(id));
    }

    @GetMapping("/list")
    public ReturnResult<List<SystemLogEntry>> listLogs(
            @PathVariable Integer id,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "200") Integer limit) throws Exception {
        return ReturnResult.ok(serverLogViewService.listLogs(id, source, level, keyword, limit));
    }
}
