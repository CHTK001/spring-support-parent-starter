package com.chua.starter.monitor.support.nginx;

import com.chua.starter.monitor.support.model.NginxConfigNodeVO;
import com.chua.starter.monitor.support.model.NginxConfigParseVO;
import com.chua.starter.monitor.support.model.NginxDirectiveMetaVO;
import com.chua.starter.monitor.support.model.dto.NginxPatchDTO;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置语法树与前端模型之间的桥：树转 VO、补丁应用、统计。
 *
 * <p>节点标识采用路径式（{@code 0/2/1}）而非 UUID。原因是前端保存时只回传改动节点的
 * 标识，服务端<strong>重新解析原始文本</strong>后必须能定位到同一个节点；路径式在
 * 文本不变时是确定的，UUID 则需要前端回传整棵树才能保持一致，反而给了前端覆盖
 * 未改动配置的能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class NginxConfigModelSupport {

    /**
     * 操作类型：修改
     */
    public static final String OP_UPDATE = "UPDATE";

    /**
     * 操作类型：新增
     */
    public static final String OP_ADD = "ADD";

    /**
     * 操作类型：删除
     */
    public static final String OP_REMOVE = "REMOVE";

    /**
     * 工具类不允许实例化。
     */
    private NginxConfigModelSupport() {
    }

    /**
     * 把语法树转成前端模型并给出统计。
     *
     * @param root     语法树根
     * @param path     配置文件路径
     * @param content  原始内容
     * @param warnings 解析告警
     * @return 解析结果
     */
    public static NginxConfigParseVO toParseVo(NginxNode root, String path, String content, List<String> warnings) {
        int[] counter = new int[5];
        NginxConfigNodeVO vo = toVo(root, "", null, counter);
        return new NginxConfigParseVO(path, content, vo,
                counter[0], counter[1], counter[2], counter[3], counter[4], warnings);
    }

    /**
     * 递归转 VO。
     *
     * <p>标识分配：虚拟根标识为空串，其第 i 个子节点标识为 {@code i}；再往下一层
     * 拼成 {@code i/j}。父标识为空串时 {@code parentId} 记为 null。</p>
     *
     * @param node     节点
     * @param id       本节点标识
     * @param parentId 父标识
     * @param counter  统计计数器：0 总数 / 1 server / 2 location / 3 upstream / 4 未登记指令
     * @return 前端模型节点
     */
    private static NginxConfigNodeVO toVo(NginxNode node, String id, String parentId, int[] counter) {
        // 虚拟根（空名）不计入统计
        if (!node.getName().isEmpty()) {
            counter[0]++;
            if (node.isBlock()) {
                switch (node.getName()) {
                    case "server" -> counter[1]++;
                    case "location" -> counter[2]++;
                    case "upstream" -> counter[3]++;
                    default -> {
                        // 其余区块不单独统计
                    }
                }
            } else if (NginxDirectiveRegistry.find(node.getName()) == null) {
                counter[4]++;
            }
        }
        List<NginxConfigNodeVO> children = new ArrayList<>();
        int index = 0;
        for (NginxNode child : node.children()) {
            String childId = id.isEmpty() ? String.valueOf(index) : id + "/" + index;
            children.add(toVo(child, childId, id.isEmpty() ? null : id, counter));
            index++;
        }
        return buildVo(node, id.isEmpty() ? null : id, id, children);
    }

    /**
     * 组装单个节点的 VO。
     *
     * @param node     节点
     * @param id       节点标识
     * @param parentId 父标识
     * @param children 子节点 VO
     * @return 前端模型节点
     */
    private static NginxConfigNodeVO buildVo(NginxNode node, String parentId, String id, List<NginxConfigNodeVO> children) {
        NginxDirectiveMeta meta = null;
        if (!node.isBlock()) {
            meta = NginxDirectiveRegistry.find(node.getName());
        }
        String scope = node.isBlock() ? NginxDirectiveRegistry.scopeOfBlock(node.getName())
                : NginxDirectiveMeta.SCOPE_HTTP;
        return new NginxConfigNodeVO(
                id.isEmpty() ? null : id,
                parentId,
                node.getName(),
                node.getHeadArg(),
                node.isBlock() ? node.displayName() : node.getName(),
                node.isBlock(),
                node.getValue(),
                scope,
                node.getStartLine(),
                node.getEndLine(),
                node.getOriginalText(),
                node.isDirty(),
                node.isRemoved(),
                meta != null,
                meta == null ? null : toMetaVo(meta),
                List.of(),
                children);
    }

    /**
     * 指令元数据转 VO。
     *
     * @param meta 元数据
     * @return VO
     */
    public static NginxDirectiveMetaVO toMetaVo(NginxDirectiveMeta meta) {
        return new NginxDirectiveMetaVO(meta.name(), meta.argType(), meta.enumValues(),
                meta.multiple(), meta.required(), meta.desc(), meta.example(), List.copyOf(meta.scopes()));
    }

    /**
     * 应用补丁列表。
     *
     * <p>顺序固定为 REMOVE → UPDATE → ADD。新增与删除会改变后续节点的路径标识，
     * 先做完不改变结构的操作，最后再做结构变更，才能保证每个补丁都作用在用户
     * 真正指向的那个节点上。</p>
     *
     * @param root    语法树根
     * @param patches 补丁列表
     * @return 告警列表
     */
    public static List<String> applyPatches(NginxNode root, List<NginxPatchDTO> patches) {
        List<String> warnings = new ArrayList<>();
        if (patches == null || patches.isEmpty()) {
            return warnings;
        }
        List<NginxPatchDTO> removes = new ArrayList<>();
        List<NginxPatchDTO> updates = new ArrayList<>();
        List<NginxPatchDTO> adds = new ArrayList<>();
        for (NginxPatchDTO p : patches) {
            if (p == null || p.op() == null) {
                continue;
            }
            switch (p.op().trim().toUpperCase(java.util.Locale.ROOT)) {
                case OP_REMOVE -> removes.add(p);
                case OP_ADD -> adds.add(p);
                default -> updates.add(p);
            }
        }
        for (NginxPatchDTO p : removes) {
            NginxNode target = resolve(root, p.nodeId());
            if (target == null) {
                warnings.add("删除失败：找不到节点 " + p.nodeId());
                continue;
            }
            target.setRemoved(true);
            markDirtyUp(root, target);
        }
        for (NginxPatchDTO p : updates) {
            NginxNode target = resolve(root, p.nodeId());
            if (target == null) {
                warnings.add("修改失败：找不到节点 " + p.nodeId());
                continue;
            }
            if (p.name() != null && !p.name().isBlank()) {
                target.setName(p.name().trim());
            }
            if (target.isBlock()) {
                if (p.headArg() != null) {
                    target.setHeadArg(p.headArg().trim());
                }
            } else if (p.value() != null) {
                target.setValue(p.value().trim());
            }
            target.setDirty(true);
            markDirtyUp(root, target);
        }
        for (NginxPatchDTO p : adds) {
            NginxNode parent = resolve(root, p.nodeId());
            if (parent == null) {
                warnings.add("新增失败：找不到父节点 " + p.nodeId());
                continue;
            }
            String name = p.name() == null ? "" : p.name().trim();
            NginxNode created;
            if (NginxBlockRegistry.isBlockName(name)) {
                created = NginxNode.block(name, p.headArg(), 0);
            } else {
                created = NginxNode.directive(name, p.value() == null ? "" : p.value(), 0);
            }
            int at = p.index() == null ? -1 : p.index();
            int insertAt;
            if (at < 0 || at > parent.children().size()) {
                parent.children().add(created);
                insertAt = parent.children().size() - 1;
            } else {
                parent.children().add(at, created);
                insertAt = at;
            }
            created.markSynthetic(true, insertionOffsetOf(parent, insertAt));
            created.setDirty(true);
            markDirtyUp(root, created);
        }
        return warnings;
    }

    /**
     * 计算新节点在原始文本中的插入偏移。
     *
     * <p>新节点在原始文本里没有对应区间，必须给一个<strong>零宽插入点</strong>，
     * 渲染器才能把它当成"插入"而不是"替换"。插入点取它在父节点子列表中的后继节点的
     * 起始偏移（插在它前面）；没有后继时插到父节点内容末尾（闭括号之前）。</p>
     *
     * @param parent 父节点
     * @param index  新节点在父节点子列表中的下标
     * @return 插入偏移
     */
    private static int insertionOffsetOf(NginxNode parent, int index) {
        List<NginxNode> siblings = parent.children();
        for (int i = index + 1; i < siblings.size(); i++) {
            NginxNode next = siblings.get(i);
            if (next.getEndOffset() > next.getStartOffset()) {
                return next.getStartOffset();
            }
        }
        if (!parent.getName().isEmpty() && parent.isBlock()) {
            // 插到父区块的闭括号之前
            return Math.max(parent.getStartOffset(), parent.getEndOffset() - 1);
        }
        return parent.getEndOffset();
    }

    /**
     * 把节点及其全部祖先标记为已改动。
     *
     * <p>渲染器只重渲染 dirty 节点，因此改动必须向上传播，否则父区块仍会吐原文，
     * 子节点的改动就丢失了。</p>
     *
     * @param root  根
     * @param node  目标节点
     */
    private static void markDirtyUp(NginxNode root, NginxNode node) {
        node.setDirty(true);
        NginxNode parent = parentOf(root, node);
        while (parent != null) {
            parent.setDirty(true);
            parent = parentOf(root, parent);
        }
    }

    /**
     * 找父节点。
     *
     * @param root 根
     * @param node 目标
     * @return 父节点，根的子节点返回根
     */
    private static NginxNode parentOf(NginxNode root, NginxNode node) {
        for (NginxNode child : root.children()) {
            if (child == node) {
                return root;
            }
            NginxNode found = parentOf(child, node);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 按路径标识定位节点。
     *
     * @param root 根
     * @param id   路径标识
     * @return 节点，标识为空或非法时返回根
     */
    public static NginxNode resolve(NginxNode root, String id) {
        if (id == null || id.isBlank()) {
            return root;
        }
        NginxNode current = root;
        for (String seg : id.trim().split("/")) {
            if (seg.isEmpty()) {
                continue;
            }
            int idx;
            try {
                idx = Integer.parseInt(seg);
            } catch (NumberFormatException e) {
                return null;
            }
            List<NginxNode> children = current.children();
            if (idx < 0 || idx >= children.size()) {
                return null;
            }
            current = children.get(idx);
        }
        return current;
    }

    /**
     * 统计两个文本的差异行数（按行集合粗略比较，用于给用户一个改动量感知）。
     *
     * @param before 变更前
     * @param after  变更后
     * @return 差异行数
     */
    public static int diffLineCount(String before, String after) {
        List<String> a = List.of((before == null ? "" : before).split("\n", -1));
        List<String> b = List.of((after == null ? "" : after).split("\n", -1));
        java.util.Set<String> setA = new java.util.HashSet<>(a);
        java.util.Set<String> setB = new java.util.HashSet<>(b);
        int removed = 0;
        for (String line : setA) {
            if (!setB.contains(line)) {
                removed++;
            }
        }
        int added = 0;
        for (String line : setB) {
            if (!setA.contains(line)) {
                added++;
            }
        }
        return removed + added;
    }
}
