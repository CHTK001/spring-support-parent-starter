package com.chua.common.support.ai.agent;

/**
 * Agent 调用用量统计（token / cost / model）。
 *
 * @author CH
 * @since 4.0.0
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

    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public Double getCost() { return cost; }
    public void setCost(Double cost) { this.cost = cost; }
    public String getModel() { return model; }
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

        public AgentUsageBuilder promptTokens(Integer promptTokens) { this.promptTokens = promptTokens; return this; }
        public AgentUsageBuilder completionTokens(Integer completionTokens) { this.completionTokens = completionTokens; return this; }
        public AgentUsageBuilder totalTokens(Integer totalTokens) { this.totalTokens = totalTokens; return this; }
        public AgentUsageBuilder cost(Double cost) { this.cost = cost; return this; }
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
