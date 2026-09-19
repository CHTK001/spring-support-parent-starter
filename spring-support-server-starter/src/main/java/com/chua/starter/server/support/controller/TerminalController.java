package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsTerminalBookmark;
import com.chua.starter.server.support.entity.MsTerminalSession;
import com.chua.starter.server.support.entity.MsTerminalSnippet;
import com.chua.starter.server.support.mapper.MsTerminalBookmarkMapper;
import com.chua.starter.server.support.mapper.MsTerminalSnippetMapper;
import com.chua.starter.server.support.service.TerminalSessionService;
import com.chua.starter.server.support.terminal.GuacdSession;
import com.chua.starter.server.support.terminal.GuacdSessionRegistry;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 终端接口：在线会话、命令片段、路径书签
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "服务器资产-终端", description = "远控会话管理与终端辅助数据")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/terminal")
@RequiredArgsConstructor
public class TerminalController {

    /** 会话审计服务 */
    private final TerminalSessionService terminalSessionService;

    /** guacd 会话注册表 */
    private final GuacdSessionRegistry sessionRegistry;

    /** 命令片段 映射器（简单 CRUD 直连） */
    private final MsTerminalSnippetMapper snippetMapper;

    /** 路径书签 映射器（简单 CRUD 直连） */
    private final MsTerminalBookmarkMapper bookmarkMapper;

    /**
     * 在线会话概览
     *
     * @return 在线会话摘要列表
     */
    @Operation(summary = "在线会话列表")
    @GetMapping("/online")
    public ReturnResult<List<Map<String, Object>>> online() {
        List<GuacdSession> sessions = sessionRegistry.snapshot();
        List<Map<String, Object>> result = new java.util.ArrayList<>(sessions.size());
        for (GuacdSession session : sessions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tunnelId", session.getTunnelId());
            item.put("hostId", session.getHostId());
            item.put("recordId", session.getSessionId());
            item.put("protocol", session.getProtocol());
            item.put("lastActiveTime", session.getLastActiveTime());
            result.add(item);
        }
        return ReturnResult.list(result);
    }

    /**
     * 强制关闭会话
     *
     * @param recordId 审计记录 标识
     * @return 结果
     */
    @Operation(summary = "强制关闭会话")
    @PostMapping("/session/{recordId}/close")
    public ReturnResult<Void> closeSession(@PathVariable Long recordId) {
        if (!sessionRegistry.closeByRecordId(recordId, "管理端强制关闭")) {
            return ReturnResult.error("会话不存在或已关闭: " + recordId);
        }
        terminalSessionService.closeRecord(recordId, "管理端强制关闭");
        return ReturnResult.success();
    }

    /**
     * 会话审计记录查询
     *
     * @param status 状态过滤，可为空
     * @return 记录列表
     */
    @Operation(summary = "会话审计记录")
    @GetMapping("/session")
    public ReturnResult<List<MsTerminalSession>> sessions(
            @RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return terminalSessionService.listByStatus(status);
        }
        return terminalSessionService.listRecent();
    }

    /**
     * 新增命令片段
     *
     * @param snippet 片段
     * @return 结果
     */
    @Operation(summary = "新增命令片段")
    @PostMapping("/snippet")
    public ReturnResult<MsTerminalSnippet> createSnippet(@RequestBody MsTerminalSnippet snippet) {
        if (snippet == null || isBlank(snippet.getMsTerminalSnippetName())) {
            return ReturnResult.error("片段名称不能为空");
        }
        snippetMapper.insert(snippet);
        return ReturnResult.ok(snippet);
    }

    /**
     * 更新命令片段
     *
     * @param id      主键
     * @param snippet 片段
     * @return 结果
     */
    @Operation(summary = "更新命令片段")
    @PutMapping("/snippet/{id}")
    public ReturnResult<MsTerminalSnippet> updateSnippet(@PathVariable Long id,
                                                         @RequestBody MsTerminalSnippet snippet) {
        MsTerminalSnippet exists = snippetMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("片段不存在: " + id);
        }
        exists.setMsTerminalSnippetName(isBlank(snippet.getMsTerminalSnippetName())
                ? exists.getMsTerminalSnippetName() : snippet.getMsTerminalSnippetName());
        exists.setMsTerminalSnippetContent(snippet.getMsTerminalSnippetContent() == null
                ? exists.getMsTerminalSnippetContent() : snippet.getMsTerminalSnippetContent());
        exists.setMsTerminalSnippetRemark(snippet.getMsTerminalSnippetRemark() == null
                ? exists.getMsTerminalSnippetRemark() : snippet.getMsTerminalSnippetRemark());
        snippetMapper.updateById(exists);
        return ReturnResult.ok(exists);
    }

    /**
     * 删除命令片段
     *
     * @param id 主键
     * @return 结果
     */
    @Operation(summary = "删除命令片段")
    @DeleteMapping("/snippet/{id}")
    public ReturnResult<Void> deleteSnippet(@PathVariable Long id) {
        snippetMapper.deleteById(id);
        return ReturnResult.success();
    }

    /**
     * 命令片段列表
     *
     * @return 列表
     */
    @Operation(summary = "命令片段列表")
    @GetMapping("/snippet")
    public ReturnResult<List<MsTerminalSnippet>> listSnippets() {
        return ReturnResult.list(snippetMapper.selectList(null));
    }

    /**
     * 新增路径书签
     *
     * @param bookmark 书签
     * @return 结果
     */
    @Operation(summary = "新增路径书签")
    @PostMapping("/bookmark")
    public ReturnResult<MsTerminalBookmark> createBookmark(@RequestBody MsTerminalBookmark bookmark) {
        if (bookmark == null || bookmark.getMsTerminalBookmarkHostId() == null
                || isBlank(bookmark.getMsTerminalBookmarkPath())) {
            return ReturnResult.error("主机与路径不能为空");
        }
        if (bookmark.getMsTerminalBookmarkSort() == null) {
            bookmark.setMsTerminalBookmarkSort(0);
        }
        bookmarkMapper.insert(bookmark);
        return ReturnResult.ok(bookmark);
    }

    /**
     * 删除路径书签
     *
     * @param id 主键
     * @return 结果
     */
    @Operation(summary = "删除路径书签")
    @DeleteMapping("/bookmark/{id}")
    public ReturnResult<Void> deleteBookmark(@PathVariable Long id) {
        bookmarkMapper.deleteById(id);
        return ReturnResult.success();
    }

    /**
     * 指定主机的书签列表
     *
     * @param hostId 主机 标识
     * @return 书签列表
     */
    @Operation(summary = "主机书签列表")
    @GetMapping("/bookmark")
    public ReturnResult<List<MsTerminalBookmark>> listBookmarks(@RequestParam Long hostId) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MsTerminalBookmark>()
                .eq(MsTerminalBookmark::getMsTerminalBookmarkHostId, hostId)
                .orderByAsc(MsTerminalBookmark::getMsTerminalBookmarkSort);
        return ReturnResult.list(bookmarkMapper.selectList(wrapper));
    }

    /**
     * 空串判断
     *
     * @param value 待判断字符串
     * @return 是否为空
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
