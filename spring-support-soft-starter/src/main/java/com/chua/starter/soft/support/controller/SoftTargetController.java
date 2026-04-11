package com.chua.starter.soft.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.soft.support.entity.SoftTarget;
import com.chua.starter.soft.support.service.SoftManagementService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/soft/targets")
public class SoftTargetController {

    private final SoftManagementService softManagementService;

    @GetMapping
    public ReturnResult<List<SoftTarget>> list() {
        return ReturnResult.ok(softManagementService.listTargets());
    }

    @PostMapping
    public ReturnResult<SoftTarget> create(@RequestBody SoftTarget target) {
        return ReturnResult.ok(softManagementService.saveTarget(target));
    }

    @PutMapping("/{id}")
    public ReturnResult<SoftTarget> update(@PathVariable Integer id, @RequestBody SoftTarget target) {
        target.setSoftTargetId(id);
        return ReturnResult.ok(softManagementService.saveTarget(target));
    }

    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Integer id) {
        softManagementService.deleteTarget(id);
        return ReturnResult.ok(true);
    }
}
