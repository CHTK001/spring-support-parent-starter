package com.chua.starter.soft.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.soft.support.entity.SoftOperationLog;
import com.chua.starter.soft.support.service.SoftManagementService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/soft/operations")
public class SoftOperationController {

    private final SoftManagementService softManagementService;

    @GetMapping
    public ReturnResult<List<SoftOperationLog>> list() {
        return ReturnResult.ok(softManagementService.listOperationLogs());
    }
}
