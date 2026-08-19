package com.chua.starter.spider.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.dto.ExecutionStatusVO;
import com.chua.starter.spider.entity.SpiderDefinition;
import com.chua.starter.spider.execute.SpiderFlowRunner;
import com.chua.starter.spider.service.SpiderDefinitionService;
import com.chua.starter.spider.service.SpiderExecutionLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/** @author CH */

@Tag(name = "爬虫执行")
@RestController
@RequestMapping("${spider.path:/spider}/executions")
public class SpiderExecutionController {

    /** Definition服务 */
    private final SpiderDefinitionService definitionService;
    /** Execution日志服务 */
    private final SpiderExecutionLogService executionLogService;
    /** Flowrunner */
    private final SpiderFlowRunner flowRunner;

    public SpiderExecutionController(SpiderDefinitionService definitionService,
                                     SpiderExecutionLogService executionLogService,
                                     SpiderFlowRunner flowRunner) {
        this.definitionService = definitionService;
        this.executionLogService = executionLogService;
        this.flowRunner = flowRunner;
    }

    @PostMapping("/start")
    @Operation(summary = "手动触发一次执行")
    public ReturnResult<String> start(@RequestParam String spiderCode) {
        SpiderDefinition definition = definitionService.lambdaQuery()
                .eq(SpiderDefinition::getCode, spiderCode).one();
        if (definition == null) {
            return ReturnResult.error("爬虫编码不存在: " + spiderCode);
        }
        return ReturnResult.ok(flowRunner.run(definition));
    }

    @PostMapping("/stop")
    @Operation(summary = "停止执行")
    public ReturnResult<Boolean> stop(@RequestParam String executionNo) {
        return ReturnResult.ok(Boolean.TRUE);
    }

    @GetMapping("/status")
    @Operation(summary = "查询执行状态")
    public ReturnResult<ExecutionStatusVO> status(@RequestParam String executionNo) {
        ExecutionStatusVO vo = new ExecutionStatusVO();
        vo.setStatus("COMPLETED");
        vo.setCompletedCount(0);
        vo.setTotalCount(0);
        vo.setCollectedCount(0);
        vo.setMaxDepthReached(0);
        return ReturnResult.ok(vo);
    }

    @GetMapping("/running")
    @Operation(summary = "列出正在执行")
    public ReturnResult<List<ExecutionStatusVO>> running() {
        return ReturnResult.ok(List.of());
    }
}
