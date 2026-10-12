package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiSessionTrace;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 会话执行轨迹 映射器。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Mapper
public interface SysAiSessionTraceMapper extends BaseMapper<SysAiSessionTrace> {

    /**
     * 批量插入轨迹。
     *
     * <p>轨迹写入是高频的（一次工具调用就可能产生十几条），逐条 insert 会把
     * 连接数与事务日志撑满，故这里用一条多值 INSERT 批量落库。
     * 调用方需自行控制批量大小，见实现方的单批上限。</p>
     *
     * @param list 待插入的轨迹集合，不可为空集
     * @return 实际插入行数
     */
    @Insert("<script>INSERT INTO sys_ai_session_trace "
            + "(sys_ai_session_trace_uid, sys_ai_session_id, chat_request_id, kind, agent_id, "
            + " span_id, span_end, tool_name, text, event_time, span_end_time, seq) VALUES "
            + "<foreach collection='list' item='t' separator=','>"
            + "(#{t.uid}, #{t.sessionId}, #{t.chatRequestId}, #{t.kind}, #{t.agentId}, "
            + " #{t.spanId}, #{t.spanEnd}, #{t.toolName}, #{t.text}, #{t.eventTime}, "
            + " #{t.spanEndTime}, #{t.seq})"
            + "</foreach></script>")
    int insertBatch(@Param("list") List<SysAiSessionTrace> list);
}
