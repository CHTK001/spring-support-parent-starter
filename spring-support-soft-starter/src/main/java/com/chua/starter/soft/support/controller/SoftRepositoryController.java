package com.chua.starter.soft.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.soft.support.entity.SoftRepository;
import com.chua.starter.soft.support.service.SoftManagementService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/soft/repositories")
public class SoftRepositoryController {

    private final SoftManagementService softManagementService;

    @GetMapping
    public ReturnResult<List<SoftRepository>> list() {
        return ReturnResult.ok(softManagementService.listRepositories());
    }

    @PostMapping
    public ReturnResult<SoftRepository> create(@RequestBody SoftRepository repository) {
        return ReturnResult.ok(softManagementService.saveRepository(repository));
    }

    @PutMapping("/{id}")
    public ReturnResult<SoftRepository> update(@PathVariable Integer id, @RequestBody SoftRepository repository) {
        repository.setSoftRepositoryId(id);
        return ReturnResult.ok(softManagementService.saveRepository(repository));
    }

    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Integer id) {
        softManagementService.deleteRepository(id);
        return ReturnResult.ok(true);
    }

    @PostMapping("/{id}/sync")
    public ReturnResult<SoftRepository> sync(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(softManagementService.syncRepository(id));
    }

    @PostMapping("/{id}/artifacts/upload")
    public ReturnResult<Map<String, Object>> uploadArtifacts(@PathVariable Integer id,
                                                             @RequestParam("files") MultipartFile[] files) throws Exception {
        return ReturnResult.ok(softManagementService.uploadRepositoryArtifacts(id, files));
    }
}
