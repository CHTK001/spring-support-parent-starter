package com.chua.starter.collect.engine.model;

import com.chua.starter.collect.dto.CollectExtractFieldDTO;
import com.chua.starter.collect.dto.CollectNodeDTO;
import com.chua.starter.collect.dto.CollectParamDTO;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 可执行节点。
 *
 * <p>对 DSL 节点的只读包装，提供参数取值与图结构访问；参数保持 DSL 中的顺序与重复项。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
public class CollectNode {

    /**
     * 原始 DSL 节点。
     */
    private final CollectNodeDTO source;

    /**
     * 出边列表。
     */
    private final List<CollectEdge> outEdges = new ArrayList<>();

    /**
     * 入边数量，汇聚节点据此判断是否所有前驱都已到达。
     */
    private int inDegree;

    /**
     * 构造可执行节点。
     *
     * @param source DSL 节点
     */
    public CollectNode(CollectNodeDTO source) {
        this.source = source;
    }

    /**
     * 节点ID。
     *
     * @return 节点ID
     */
    public String id() {
        return source.id();
    }

    /**
     * 节点类型。
     *
     * @return 节点类型
     */
    public String shape() {
        return source.shape();
    }

    /**
     * 节点名称，为空时回退为"类型-ID"。
     *
     * @return 节点名称
     */
    public String name() {
        String name = source.name();
        if (name == null || name.isBlank()) {
            return source.shape() + "-" + source.id();
        }
        return name;
    }

    /**
     * 按参数名取首个非空值。
     *
     * @param name 参数名
     * @return 参数值，不存在返回空串
     */
    public String param(String name) {
        for (CollectParamDTO param : params()) {
            if (name.equals(param.name()) && param.value() != null && !param.value().isBlank()) {
                return param.value().trim();
            }
        }
        return "";
    }

    /**
     * 按参数名取全部值，用于请求头、变量赋值等可重复参数。
     *
     * @param name 参数名
     * @return 参数值列表，不存在返回空列表
     */
    public List<CollectParamDTO> paramsOf(String name) {
        List<CollectParamDTO> list = new ArrayList<>();
        for (CollectParamDTO param : params()) {
            if (name.equals(param.name()) && param.value() != null && !param.value().isBlank()) {
                list.add(param);
            }
        }
        return list;
    }

    /**
     * 取一组键值对参数，用于请求头、Cookie、变量赋值等可重复配置。
     *
     * <p>DSL 中每行存成 name 为 {@code group:键名}、value 为值的参数，
     * 本方法剥掉前缀后按声明顺序返回，重复键名一并保留。</p>
     *
     * @param group 参数组名
     * @return 键值对列表，不存在返回空列表
     */
    public List<CollectParamDTO> pairsOf(String group) {
        String prefix = group + ":";
        List<CollectParamDTO> list = new ArrayList<>();
        for (CollectParamDTO param : params()) {
            String name = param.name();
            if (name == null || !name.startsWith(prefix)) {
                continue;
            }
            list.add(new CollectParamDTO(name.substring(prefix.length()).trim(),
                    param.value(), param.description()));
        }
        return list;
    }

    /**
     * 整型参数。
     *
     * @param name         参数名
     * @param defaultValue 默认值
     * @return 参数值，非数字返回默认值
     */
    public int intParam(String name, int defaultValue) {
        String value = param(name);
        if (value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * 长整型参数。
     *
     * @param name         参数名
     * @param defaultValue 默认值
     * @return 参数值，非数字返回默认值
     */
    public long longParam(String name, long defaultValue) {
        String value = param(name);
        if (value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * 布尔参数，接受 1/true。
     *
     * @param name         参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    public boolean boolParam(String name, boolean defaultValue) {
        String value = param(name);
        if (value.isEmpty()) {
            return defaultValue;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    /**
     * 抽取字段声明。
     *
     * @return 字段声明列表
     */
    public List<CollectExtractFieldDTO> fields() {
        return source.fields() == null ? List.of() : source.fields();
    }

    /**
     * 节点参数列表。
     *
     * @return 参数列表
     */
    public List<CollectParamDTO> params() {
        return source.params() == null ? List.of() : source.params();
    }

    /**
     * 登记出边。
     *
     * @param edge 出边视图
     */
    public void addOutEdge(CollectEdge edge) {
        outEdges.add(edge);
    }

    /**
     * 入度加一。
     */
    public void increaseInDegree() {
        inDegree++;
    }
}
