package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 单条配置改动（补丁）。
 *
 * <p>UI 模式不回传整棵树，只回传用户实际动过的节点。后端会<strong>重新解析原始文本</strong>
 * 重建语法树，再按 {@code nodeId} 定位并应用补丁——这样前端无法通过伪造整棵树来
 * 覆盖掉没打算改的配置。</p>
 *
 * <p>{@code nodeId} 是路径式稳定标识（形如 {@code 0/2/1}，表示根的第 0 个子节点、
 * 其第 2 个子节点的第 1 个子节点）。为保证定位稳定，服务端按
 * <strong>先 UPDATE/REMOVE，后 ADD</strong> 的顺序应用补丁：新增或删除会让后续节点的
 * 路径标识发生偏移，混序应用会导致改错地方。</p>
 *
 * @param nodeId  目标节点标识；ADD 时为目标父节点标识
 * @param op      操作类型：UPDATE / ADD / REMOVE
 * @param name    指令名或区块名，ADD 与 UPDATE 必填
 * @param headArg 区块头部参数
 * @param value   叶子指令参数
 * @param index   ADD 时的插入位置，缺省追加到末尾
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NginxPatchDTO(
        String nodeId,
        String op,
        String name,
        String headArg,
        String value,
        Integer index
) {
}
