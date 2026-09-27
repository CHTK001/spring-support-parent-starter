package com.chua.starter.datasearch.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasearch.pojo.typhoon.TyphoonDetailVO;
import com.chua.starter.datasearch.pojo.typhoon.TyphoonItemVO;
import com.chua.starter.datasearch.service.TyphoonDataService;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 顶栏台风接口。
 *
 * <p>前端只探测 {@code /active}（详情与列表同属本控制器），故该路径必须匿名可访问、
 * 无参也能命中映射。空列表是「当前无活跃台风」的权威结论，失败才是回落信号。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Tag(name = "顶栏台风接口")
@RestController
@RequestMapping("/v2/typhoon")
@RequiredArgsConstructor
public class TyphoonV2Controller {

    /**
     * 台风数据服务
    */
    private final TyphoonDataService typhoonDataService;

    /**
     * 当前活跃台风列表
     *
     * @return 活跃台风列表；确无活跃台风时为空数组
     */
    @Operation(summary = "当前活跃台风列表")
    @TokenForIgnore
    @GetMapping("/active")
    public ReturnResult<List<TyphoonItemVO>> active() {
        try {
            List<TyphoonItemVO> list = typhoonDataService.activeTyphoons();
            return list == null ? ReturnResult.error("无可用台风数据源") : ReturnResult.ok(list);
        } catch (Exception e) {
            log.warn("[typhoon] 活跃台风列表失败: {}", e.getMessage());
            return ReturnResult.error("台风列表查询失败：" + e.getMessage());
        }
    }

    /**
     * 单个台风详情（历史路径 + 预报路径）
     *
     * @param id 台风编号
     * @return 台风详情
     */
    @Operation(summary = "台风详情")
    @Parameter(name = "id", description = "台风编号，如 202618")
    @TokenForIgnore
    @GetMapping("/detail")
    public ReturnResult<TyphoonDetailVO> detail(@RequestParam("id") String id) {
        try {
            TyphoonDetailVO detail = typhoonDataService.detail(id);
            return detail == null ? ReturnResult.error("台风详情不可用: " + id) : ReturnResult.ok(detail);
        } catch (Exception e) {
            log.warn("[typhoon] 台风详情失败: id={}, msg={}", id, e.getMessage());
            return ReturnResult.error("台风详情查询失败：" + e.getMessage());
        }
    }
}
