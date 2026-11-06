package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.collect.entity.CollectCategory;
import com.chua.starter.collect.entity.CollectFlow;
import com.chua.starter.collect.mapper.CollectCategoryMapper;
import com.chua.starter.collect.mapper.CollectFlowMapper;
import com.chua.starter.collect.service.CollectCategoryService;
import com.chua.starter.collect.vo.CollectCategoryVO;
import com.chua.starter.collect.vo.CollectOptionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集分类服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectCategoryServiceImpl extends ServiceImpl<CollectCategoryMapper, CollectCategory>
        implements CollectCategoryService {

    /**
     * 顶级分类的父ID。
     */
    private static final int ROOT_PID = 0;

    /**
     * 路径分隔符。
     */
    private static final String SEPARATOR = "/";

    /**
     * 流程表访问，用于删除前的占用检查。
     */
    private final CollectFlowMapper flowMapper;

    @Override
    public List<CollectCategoryVO> tree() {
        List<CollectCategory> all = listOrdered();
        Map<Integer, CollectCategoryVO> index = new LinkedHashMap<>();
        for (CollectCategory category : all) {
            index.put(category.getCollectCategoryId(), toVo(category));
        }
        List<CollectCategoryVO> roots = new ArrayList<>();
        for (CollectCategoryVO vo : index.values()) {
            CollectCategoryVO parent = vo.getCollectCategoryPid() == null
                    ? null : index.get(vo.getCollectCategoryPid());
            if (parent == null) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        fillFlowCount(roots);
        return roots;
    }

    @Override
    public List<CollectOptionVO> options() {
        List<CollectOptionVO> options = new ArrayList<>();
        options.add(new CollectOptionVO(String.valueOf(ROOT_PID), "未分类"));
        for (CollectCategory category : listOrdered()) {
            options.add(new CollectOptionVO(String.valueOf(category.getCollectCategoryId()),
                    category.getCollectCategoryName()));
        }
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectCategory addCategory(CollectCategory category) {
        requireName(category);
        checkParent(category.getCollectCategoryPid(), null);
        if (category.getCollectCategorySort() == null) {
            category.setCollectCategorySort(0);
        }
        category.setCollectCategoryId(null);
        category.setCollectCategoryTreeId(null);
        save(category);
        CollectCategory filled = new CollectCategory();
        filled.setCollectCategoryId(category.getCollectCategoryId());
        filled.setCollectCategoryTreeId(treeIdOf(category.getCollectCategoryPid(), category.getCollectCategoryId()));
        updateById(filled);
        category.setCollectCategoryTreeId(filled.getCollectCategoryTreeId());
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean editCategory(CollectCategory category) {
        CollectCategory exists = requireCategory(category.getCollectCategoryId());
        requireName(category);
        checkParent(category.getCollectCategoryPid(), exists.getCollectCategoryId());
        category.setCollectCategoryTreeId(treeIdOf(category.getCollectCategoryPid(), exists.getCollectCategoryId()));
        return updateById(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCategory(Integer categoryId) {
        requireCategory(categoryId);
        long children = count(Wrappers.<CollectCategory>lambdaQuery()
                .eq(CollectCategory::getCollectCategoryPid, categoryId));
        if (children > 0) {
            throw new IllegalArgumentException("请先删除子分类");
        }
        long flows = flowMapper.selectCount(Wrappers.<CollectFlow>lambdaQuery()
                .eq(CollectFlow::getCollectFlowCategoryId, categoryId)
                .eq(CollectFlow::getCollectFlowIsDeleted, 0));
        if (flows > 0) {
            throw new IllegalArgumentException("该分类下还有 " + flows + " 个流程，不能删除");
        }
        return removeById(categoryId);
    }

    /**
     * 按排序取全部分类。
     *
     * @return 分类列表
     */
    private List<CollectCategory> listOrdered() {
        return list(Wrappers.<CollectCategory>lambdaQuery()
                .orderByAsc(CollectCategory::getCollectCategorySort)
                .orderByAsc(CollectCategory::getCollectCategoryId));
    }

    /**
     * 按主键取分类，不存在直接拒绝。
     *
     * @param categoryId 分类ID
     * @return 分类
     */
    private CollectCategory requireCategory(Integer categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("分类ID不能为空");
        }
        CollectCategory category = getById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("分类不存在: " + categoryId);
        }
        return category;
    }

    /**
     * 校验分类名称。
     *
     * @param category 分类
     */
    private void requireName(CollectCategory category) {
        String name = category.getCollectCategoryName() == null
                ? "" : category.getCollectCategoryName().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
        category.setCollectCategoryName(name);
    }

    /**
     * 校验父分类存在且不形成回环。
     *
     * @param pid       父分类ID
     * @param currentId 当前分类ID，新增时传 null
     */
    private void checkParent(Integer pid, Integer currentId) {
        if (pid == null || pid == ROOT_PID) {
            return;
        }
        if (pid.equals(currentId)) {
            throw new IllegalArgumentException("父分类不能是自己");
        }
        CollectCategory parent = getById(pid);
        if (parent == null) {
            throw new IllegalArgumentException("父分类不存在: " + pid);
        }
        String path = parent.getCollectCategoryTreeId();
        if (currentId != null && path != null && path.contains(SEPARATOR + currentId + SEPARATOR)) {
            throw new IllegalArgumentException("父分类在自己的子树里，会造成回环");
        }
    }

    /**
     * 拼出分类路径链。
     *
     * @param pid 父分类ID
     * @param id  当前分类ID
     * @return 形如 /0/12/34/ 的路径
     */
    private String treeIdOf(Integer pid, Integer id) {
        String prefix = SEPARATOR + ROOT_PID + SEPARATOR;
        if (pid != null && pid != ROOT_PID) {
            CollectCategory parent = getById(pid);
            if (parent != null && parent.getCollectCategoryTreeId() != null
                    && !parent.getCollectCategoryTreeId().isBlank()) {
                prefix = parent.getCollectCategoryTreeId();
            } else if (parent != null) {
                prefix = SEPARATOR + pid + SEPARATOR;
            }
        }
        return prefix + id + SEPARATOR;
    }

    /**
     * 实体转出参。
     *
     * @param category 分类
     * @return 出参
     */
    private CollectCategoryVO toVo(CollectCategory category) {
        CollectCategoryVO vo = new CollectCategoryVO();
        vo.setCollectCategoryId(category.getCollectCategoryId());
        vo.setCollectCategoryName(category.getCollectCategoryName());
        vo.setCollectCategoryPid(category.getCollectCategoryPid() == null
                ? ROOT_PID : category.getCollectCategoryPid());
        vo.setCollectCategorySort(category.getCollectCategorySort());
        vo.setCollectCategoryRemark(category.getCollectCategoryRemark());
        vo.setFlowCount(0);
        return vo;
    }

    /**
     * 一次分组统计后回填各分类直属流程数。
     *
     * @param nodes 分类节点
     */
    private void fillFlowCount(List<CollectCategoryVO> nodes) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : flowMapper.selectMaps(Wrappers.<CollectFlow>query()
                .select("collect_flow_category_id as cid", "count(1) as cnt")
                .eq("collect_flow_is_deleted", 0)
                .groupBy("collect_flow_category_id"))) {
            Object key = row.get("cid");
            Object value = row.get("cnt");
            if (key instanceof Number categoryId && value instanceof Number count) {
                counts.put(categoryId.intValue(), count.intValue());
            }
        }
        applyFlowCount(nodes, counts);
    }

    /**
     * 递归写入流程数。
     *
     * @param nodes  分类节点
     * @param counts 分类ID到流程数
     */
    private void applyFlowCount(List<CollectCategoryVO> nodes, Map<Integer, Integer> counts) {
        for (CollectCategoryVO node : nodes) {
            node.setFlowCount(counts.getOrDefault(node.getCollectCategoryId(), 0));
            applyFlowCount(node.getChildren(), counts);
        }
    }
}
