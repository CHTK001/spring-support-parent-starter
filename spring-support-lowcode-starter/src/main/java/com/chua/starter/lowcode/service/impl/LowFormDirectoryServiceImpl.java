package com.chua.starter.lowcode.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.lowcode.entity.LowForm;
import com.chua.starter.lowcode.entity.LowFormDirectory;
import com.chua.starter.lowcode.entity.LowFormPage;
import com.chua.starter.lowcode.mapper.LowFormDirectoryMapper;
import com.chua.starter.lowcode.mapper.LowFormMapper;
import com.chua.starter.lowcode.mapper.LowFormPageMapper;
import com.chua.starter.lowcode.service.LowFormDirectoryService;
import com.chua.starter.lowcode.vo.LowFormDirectoryVO;
import com.chua.starter.lowcode.vo.LowFormOptionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 低代码-目录服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowFormDirectoryServiceImpl extends ServiceImpl<LowFormDirectoryMapper, LowFormDirectory>
        implements LowFormDirectoryService {

    /**
     * 顶级目录的父ID。
     */
    private static final int ROOT_PID = 0;

    /**
     * 目录路径分隔符。
     */
    private static final String SEPARATOR = "/";

    private final LowFormMapper formMapper;
    private final LowFormPageMapper pageMapper;

    @Override
    public List<LowFormDirectoryVO> tree() {
        List<LowFormDirectory> all = listOrdered();
        Map<Integer, LowFormDirectoryVO> index = new LinkedHashMap<>();
        for (LowFormDirectory directory : all) {
            index.put(directory.getLowFormDirectoryId(), toVo(directory));
        }
        List<LowFormDirectoryVO> roots = new ArrayList<>();
        for (LowFormDirectoryVO vo : index.values()) {
            LowFormDirectoryVO parent = vo.getLowFormDirectoryPid() == null
                    ? null : index.get(vo.getLowFormDirectoryPid());
            if (parent == null) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        // 直属统计数据
        Map<Integer, Integer> formCounts = formCounts();
        Map<Integer, Integer> pageCounts = pageCounts();
        List<LowFormDirectoryVO> vos = new ArrayList<>(index.values());
        for (LowFormDirectoryVO vo : vos) {
            vo.setFormCount(formCounts.getOrDefault(vo.getLowFormDirectoryId(), 0));
            vo.setPageCount(pageCounts.getOrDefault(vo.getLowFormDirectoryId(), 0));
        }
        // 自底向上合并：父目录统计覆盖其深子目录。
        // 只按「列表逆序」并不保证子先于父（列表按 sort 排序），因此按树深降序保证先处理深层。
        List<LowFormDirectoryVO> byDepthDesc = new ArrayList<>(vos);
        byDepthDesc.sort((a, b) -> Integer.compare(depthOf(b, index), depthOf(a, index)));
        for (LowFormDirectoryVO vo : byDepthDesc) {
            int pid = vo.getLowFormDirectoryPid() == null ? ROOT_PID : vo.getLowFormDirectoryPid();
            if (pid == ROOT_PID) {
                continue;
            }
            LowFormDirectoryVO parent = index.get(pid);
            if (parent == null) {
                continue;
            }
            parent.setFormCount(parent.getFormCount() + vo.getFormCount());
            parent.setPageCount(parent.getPageCount() + vo.getPageCount());
        }
        return roots;
    }

    /**
     * 目录在树中的深度，根目录为 0。
     *
     * @param vo    目录出参
     * @param index 目录索引
     * @return 深度
     */
    private int depthOf(LowFormDirectoryVO vo, Map<Integer, LowFormDirectoryVO> index) {
        int depth = 0;
        LowFormDirectoryVO current = vo;
        while (current != null && current.getLowFormDirectoryPid() != null
                && current.getLowFormDirectoryPid() != ROOT_PID) {
            current = index.get(current.getLowFormDirectoryPid());
            // 防御脏数据造成的死循环
            if (++depth > index.size()) {
                break;
            }
        }
        return depth;
    }

    @Override
    public List<LowFormOptionVO> options() {
        List<LowFormOptionVO> options = new ArrayList<>();
        for (LowFormDirectory directory : listOrdered()) {
            options.add(new LowFormOptionVO(String.valueOf(directory.getLowFormDirectoryId()),
                    directory.getLowFormDirectoryName()));
        }
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowFormDirectory addDirectory(LowFormDirectory directory) {
        requireName(directory);
        checkParentId(directory.getLowFormDirectoryPid(), null);
        if (directory.getLowFormDirectorySort() == null) {
            directory.setLowFormDirectorySort(0);
        }
        directory.setLowFormDirectoryId(null);
        directory.setLowFormDirectoryTreeId(null);
        save(directory);
        LowFormDirectory filled = new LowFormDirectory();
        filled.setLowFormDirectoryId(directory.getLowFormDirectoryId());
        filled.setLowFormDirectoryTreeId(treeIdOf(directory.getLowFormDirectoryPid(),
                directory.getLowFormDirectoryId()));
        updateById(filled);
        directory.setLowFormDirectoryTreeId(filled.getLowFormDirectoryTreeId());
        return directory;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean editDirectory(LowFormDirectory directory) {
        LowFormDirectory exists = requireDirectory(directory.getLowFormDirectoryId());
        requireName(directory);
        // pid 未传表示「只改名称/排序」，沿用原父级；否则 null 会被当成根，把子树静默挂到顶层。
        Integer pid = directory.getLowFormDirectoryPid() == null
                ? exists.getLowFormDirectoryPid() : directory.getLowFormDirectoryPid();
        directory.setLowFormDirectoryPid(pid);
        checkParentId(pid, exists.getLowFormDirectoryId());
        String oldTreeId = exists.getLowFormDirectoryTreeId();
        String newTreeId = treeIdOf(pid, exists.getLowFormDirectoryId());
        directory.setLowFormDirectoryTreeId(newTreeId);
        boolean updated = updateById(directory);
        if (updated && oldTreeId != null && !oldTreeId.equals(newTreeId)) {
            // 目录换了父级，子孙的路径链前缀必须跟着搬，否则按 tree_id 的层级判断全部失效。
            cascadeTreeId(oldTreeId, newTreeId);
        }
        return updated;
    }

    /**
     * 把一个子树的路径链前缀从旧值替换为新值。
     *
     * @param oldTreeId 旧前缀
     * @param newTreeId 新前缀
     */
    private void cascadeTreeId(String oldTreeId, String newTreeId) {
        List<LowFormDirectory> descendants = list(Wrappers.<LowFormDirectory>lambdaQuery()
                .likeRight(LowFormDirectory::getLowFormDirectoryTreeId, oldTreeId));
        for (LowFormDirectory child : descendants) {
            String treeId = child.getLowFormDirectoryTreeId();
            if (treeId == null || treeId.equals(oldTreeId)) {
                continue;
            }
            LowFormDirectory patch = new LowFormDirectory();
            patch.setLowFormDirectoryId(child.getLowFormDirectoryId());
            patch.setLowFormDirectoryTreeId(newTreeId + treeId.substring(oldTreeId.length()));
            updateById(patch);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDirectory(Integer directoryId) {
        requireDirectory(directoryId);
        long children = count(Wrappers.<LowFormDirectory>lambdaQuery()
                .eq(LowFormDirectory::getLowFormDirectoryPid, directoryId));
        if (children > 0) {
            throw new IllegalArgumentException("请先删除子目录");
        }
        // 目录下的表单/页面会因目录消失而变成孤儿记录，必须先移走或删除。
        long forms = formMapper.selectCount(Wrappers.<LowForm>lambdaQuery()
                .eq(LowForm::getLowFormDirectoryId, directoryId));
        if (forms > 0) {
            throw new IllegalArgumentException("该目录下还有 " + forms + " 个表单，请先移走或删除");
        }
        long pages = pageMapper.selectCount(Wrappers.<LowFormPage>lambdaQuery()
                .eq(LowFormPage::getLowPageDirectoryId, directoryId));
        if (pages > 0) {
            throw new IllegalArgumentException("该目录下还有 " + pages + " 个页面，请先移走或删除");
        }
        return removeById(directoryId);
    }

    /**
     * 按排序取全部目录。
     *
     * @return 目录列表
     */
    private List<LowFormDirectory> listOrdered() {
        return list(Wrappers.<LowFormDirectory>lambdaQuery()
                .orderByAsc(LowFormDirectory::getLowFormDirectorySort)
                .orderByAsc(LowFormDirectory::getLowFormDirectoryId));
    }

    /**
     * 按主键取目录，不存在直接拒绝。
     *
     * @param directoryId 目录ID
     * @return 目录
     */
    private LowFormDirectory requireDirectory(Integer directoryId) {
        if (directoryId == null) {
            throw new IllegalArgumentException("目录ID不能为空");
        }
        LowFormDirectory directory = getById(directoryId);
        if (directory == null) {
            throw new IllegalArgumentException("目录不存在: " + directoryId);
        }
        return directory;
    }

    /**
     * 校验目录名称。
     *
     * @param directory 目录
     */
    private void requireName(LowFormDirectory directory) {
        String name = directory.getLowFormDirectoryName() == null
                ? "" : directory.getLowFormDirectoryName().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("目录名称不能为空");
        }
        directory.setLowFormDirectoryName(name);
    }

    /**
     * 校验父目录是否存在且不形成回环。
     *
     * @param pid       父目录ID
     * @param currentId 当前目录ID，新增时传 null
     */
    private void checkParentId(Integer pid, Integer currentId) {
        if (pid == null || pid == ROOT_PID) {
            return;
        }
        if (pid.equals(currentId)) {
            throw new IllegalArgumentException("父目录不能是自己");
        }
        LowFormDirectory parent = getById(pid);
        if (parent == null) {
            throw new IllegalArgumentException("父目录不存在: " + pid);
        }
        if (currentId == null) {
            return;
        }
        // 双保险：tree_id 可能为空或脏，路径判定之外再沿父链实际走一遍。
        java.util.Set<Integer> visited = new java.util.HashSet<>();
        LowFormDirectory cursor = parent;
        while (cursor != null && cursor.getLowFormDirectoryPid() != null
                && cursor.getLowFormDirectoryPid() != ROOT_PID) {
            if (currentId.equals(cursor.getLowFormDirectoryPid()) || !visited.add(cursor.getLowFormDirectoryId())) {
                throw new IllegalArgumentException("父目录在自己的子树里，会造成回环");
            }
            cursor = getById(cursor.getLowFormDirectoryPid());
        }
        if (currentId.equals(parent.getLowFormDirectoryPid())) {
            throw new IllegalArgumentException("父目录在自己的子树里，会造成回环");
        }
    }

    /**
     * 拼出目录路径链。
     *
     * @param pid 父目录ID
     * @param id  当前目录ID
     * @return 形如 /0/12/34/ 的路径
     */
    private String treeIdOf(Integer pid, Integer id) {
        String prefix = SEPARATOR + ROOT_PID + SEPARATOR;
        if (pid != null && pid != ROOT_PID) {
            LowFormDirectory parent = getById(pid);
            if (parent != null && parent.getLowFormDirectoryTreeId() != null
                    && !parent.getLowFormDirectoryTreeId().isBlank()) {
                prefix = parent.getLowFormDirectoryTreeId();
            } else if (parent != null) {
                prefix = SEPARATOR + pid + SEPARATOR;
            }
        }
        return prefix + id + SEPARATOR;
    }

    /**
     * 实体转出参。
     *
     * @param directory 目录
     * @return 出参
     */
    private LowFormDirectoryVO toVo(LowFormDirectory directory) {
        LowFormDirectoryVO vo = new LowFormDirectoryVO();
        vo.setLowFormDirectoryId(directory.getLowFormDirectoryId());
        vo.setLowFormDirectoryName(directory.getLowFormDirectoryName());
        vo.setLowFormDirectoryPid(directory.getLowFormDirectoryPid() == null
                ? ROOT_PID : directory.getLowFormDirectoryPid());
        vo.setLowFormDirectorySort(directory.getLowFormDirectorySort());
        vo.setLowFormDirectoryRemark(directory.getLowFormDirectoryRemark());
        vo.setFormCount(0);
        vo.setPageCount(0);
        return vo;
    }

    /**
     * 一次性统计所有目录的直属表单数。
     *
     * @return 目录ID -> 直属表单数
     */
    private Map<Integer, Integer> formCounts() {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> row : formMapper.selectMaps(Wrappers.<LowForm>query()
                .select("low_form_directory_id AS dir", "count(1) AS cnt")
                .groupBy("low_form_directory_id"))) {
            Object key = row.get("dir");
            Object value = row.get("cnt");
            if (key instanceof Number categoryId && value instanceof Number count) {
                result.put(categoryId.intValue(), count.intValue());
            }
        }
        return result;
    }

    /**
     * 一次性统计所有目录的直属页面数。
     *
     * @return 目录ID -> 直属页面数
     */
    private Map<Integer, Integer> pageCounts() {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> row : pageMapper.selectMaps(Wrappers.<LowFormPage>query()
                .select("low_page_directory_id AS dir", "count(1) AS cnt")
                .groupBy("low_page_directory_id"))) {
            Object key = row.get("dir");
            Object value = row.get("cnt");
            if (key instanceof Number categoryId && value instanceof Number count) {
                result.put(categoryId.intValue(), count.intValue());
            }
        }
        return result;
    }
}
