package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.model.vo.TunnelVO;
import com.chua.starter.server.support.service.TunnelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;

/**
 * 主机隧道 —— 正向/反向 SSH 端口转发真实接口
 *
 * <p>端点与前端 {@code pages/server/src/api/index.ts} 的 tunnel 桩严格对齐：
 * 前缀 {@code /ms/server/host/{hostId}/tunnel}，响应统一 {@code ReturnResult}。
 * 建立（POST）后默认 stopped，需 PUT state=RUNNING 才启动转发。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Tag(name = "监控-主机隧道", description = "正向/反向 SSH 端口转发（真实接口，SshClient 托管）")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/host/{hostId}/tunnel")
@RequiredArgsConstructor
public class TunnelController {

    /**
     * 隧道服务
     */
    private final TunnelService tunnelService;

    /**
     * 列出主机全部隧道
     *
     * @param hostId 主机 标识
     * @return 隧道列表
     */
    @Operation(summary = "隧道列表")
    @GetMapping
    public ReturnResult<List<TunnelVO>> list(@PathVariable Long hostId) {
        return tunnelService.list(hostId);
    }

    /**
     * 建立隧道
     *
     * @param hostId 主机 标识
     * @param d      隧道定义
     * @return 新建隧道
     */
    @Operation(summary = "建立隧道")
    @PostMapping
    public ReturnResult<TunnelVO> create(@PathVariable Long hostId, @RequestBody TunnelVO d) {
        return tunnelService.create(hostId, d);
    }

    /**
     * 切换隧道运行态
     *
     * @param hostId 主机 标识
     * @param id     隧道 id
     * @param state  RUNNING / STOPPED
     * @return 更新后隧道
     */
    @Operation(summary = "切换隧道运行态")
    @PutMapping("/{id}/state")
    public ReturnResult<TunnelVO> updateState(@PathVariable Long hostId,
                                              @PathVariable Long id,
                                              @RequestParam String state) {
        return tunnelService.updateState(hostId, id, state);
    }

    /**
     * 删除隧道
     *
     * @param hostId 主机 标识
     * @param id     隧道 id
     * @return 是否删除成功
     */
    @Operation(summary = "删除隧道")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long hostId, @PathVariable Long id) {
        return tunnelService.delete(hostId, id);
    }
}
