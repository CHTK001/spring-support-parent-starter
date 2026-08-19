package com.chua.common.support.ai.agent;

/**
 * Agent 调用用量统计（token / cost / model）。
 *
 * @since 4.0.0
 * @author CH
 */
public class AgentUsage {

    /**
     * 输入 token 数
     */
    private Integer promptTokens;

    /**
     * 输出 token 数
     */
    private Integer completionTokens;

    /**
     * 总 token 数
     */
    private Integer totalTokens;

    /**
     * 成本（元）
     */
    private Double cost;

    /**
     * 关联模型
     */
    private String model;

    /**
     * 默认构造。
     */
    public AgentUsage() {
    }

    /**
     * 全参构造。
     */
    public AgentUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens, Double cost, String model) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.cost = cost;
        this.model = model;
    }

    /** 获取PromptTokens */
    public Integer getPromptTokens() { return promptTokens; }
    /** 设置PromptTokens */
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    /** 获取CompletionTokens */
    public Integer getCompletionTokens() { return completionTokens; }
    /** 设置CompletionTokens */
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    /** 获取总计Tokens */
    public Integer getTotalTokens() { return totalTokens; }
    /** 设置总计Tokens */
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    /** 获取Cost */
    public Double getCost() { return cost; }
    /** 设置Cost */
    public void setCost(Double cost) { this.cost = cost; }
    /** 获取Model */
    public String getModel() { return model; }
    /** 设置Model */
    public void setModel(String model) { this.model = model; }

    /**
     * @return 建造器实例
     */
    public static AgentUsageBuilder builder() {
        return new AgentUsageBuilder();
    }

    /**
     * AgentUsage 建造器。
     */
    public static class AgentUsageBuilder {

        /**
         * 输入 token 数
         */
        private Integer promptTokens;

        /**
         * 输出 token 数
         */
        private Integer completionTokens;

        /**
         * 总 token 数
         */
        private Integer totalTokens;

        /**
         * 成本（元）
         */
        private Double cost;

        /**
         * 关联模型
         */
        private String model;

        /** PromptTokens */
        public AgentUsageBuilder promptTokens(Integer promptTokens) { this.promptTokens = promptTokens; return this; }
        /** CompletionTokens */
        public AgentUsageBuilder completionTokens(Integer completionTokens) { this.completionTokens = completionTokens; return this; }
        /** 总计Tokens */
        public AgentUsageBuilder totalTokens(Integer totalTokens) { this.totalTokens = totalTokens; return this; }
        /** Cost */
        public AgentUsageBuilder cost(Double cost) { this.cost = cost; return this; }
        /** Model */
        public AgentUsageBuilder model(String model) { this.model = model; return this; }

        /**
         * 构造 AgentUsage 实例。
         *
         * @return AgentUsage 实例
         */
        public AgentUsage build() {
            return new AgentUsage(promptTokens, completionTokens, totalTokens, cost, model);
        }
    }
}
