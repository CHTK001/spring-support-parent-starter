package com.chua.starter.spider.support.controller;

import com.chua.starter.spider.support.domain.SpiderWorkbenchTab;
import com.chua.starter.spider.support.repository.SpiderWorkbenchTabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Workbench Tab 接口。
 *
 * @author CH
 */
@RestController
@RequestMapping("/v1/spider/workbench/tabs")
@RequiredArgsConstructor
public class SpiderWorkbenchController {

    private final SpiderWorkbenchTabRepository tabRepository;

    /** GET /v1/spider/workbench/tabs */
    @GetMapping
    public ResponseEntity<?> listTabs() {
        return ResponseEntity.ok(tabRepository.findAllOrdered());
    }

    /** POST /v1/spider/workbench/tabs */
    @PostMapping
    public ResponseEntity<?> createTab(@RequestBody SpiderWorkbenchTab tab) {
        tabRepository.save(tab);
        return ResponseEntity.status(HttpStatus.CREATED).body(tab);
    }

    /** PUT /v1/spider/workbench/tabs/{tabId} */
    @PutMapping("/{tabId}")
    public ResponseEntity<?> updateTab(@PathVariable Long tabId,
                                       @RequestBody SpiderWorkbenchTab tab) {
        tab.setId(tabId);
        boolean updated = tabRepository.updateById(tab);
        if (!updated) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Tab [" + tabId + "] 不存在"));
        }
        return ResponseEntity.ok(tab);
    }

    /** DELETE /v1/spider/workbench/tabs/{tabId} */
    @DeleteMapping("/{tabId}")
    public ResponseEntity<?> deleteTab(@PathVariable Long tabId) {
        boolean removed = tabRepository.removeById(tabId);
        if (!removed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Tab [" + tabId + "] 不存在"));
        }
        return ResponseEntity.noContent().build();
    }
}
