package com.chua.common.support.ai.agent;

/**
   * 智能体 调用用量统计（令牌 / cost / 模型）。
 *
 * @since 4.0.0
 * @author CH
 */
public class AgentUsage {

    /**
      * 输入 令牌 数
     */
    private Integer promptTokens;

    /**
      * 输出 令牌 数
     */
    private Integer completionTokens;

    /**
      * 总 令牌 数
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
     * @param promptTokens 提示符令牌
     * @param completionTokens 完成令牌
     * @param totalTokens total令牌
     * @param cost cost
     * @param model 模型
     */
    public AgentUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens, Double cost, String model) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.cost = cost;
        this.model = model;
    }

    /**
     * 获取提示符令牌
     *
     * @return 获取提示符令牌的结果
     */
    public Integer getPromptTokens() { return promptTokens; }
    /**
     * 设置提示符令牌
     *
     * @param promptTokens 提示符令牌
     */
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    /**
     * 获取完成令牌
     *
     * @return 获取完成令牌的结果
     */
    public Integer getCompletionTokens() { return completionTokens; }
    /**
     * 设置完成令牌
     *
     * @param completionTokens 完成令牌
     */
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    /**
     * 获取总计令牌
     *
     * @return 获取total令牌的结果
     */
    public Integer getTotalTokens() { return totalTokens; }
    /**
     * 设置总计令牌
     *
     * @param totalTokens total令牌
     */
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    /**
     * 获取Cost
     *
     * @return 获取cost的结果
     */
    public Double getCost() { return cost; }
    /**
     * 设置Cost
     *
     * @param cost cost
     */
    public void setCost(Double cost) { this.cost = cost; }
    /**
     * 获取模型
     *
     * @return 获取模型的结果
     */
    public String getModel() { return model; }
    /**
     * 设置模型
     *
     * @param model 模型
     */
    public void setModel(String model) { this.model = model; }

    /**
     * @return 建造器实例
     */
    public static AgentUsageBuilder builder() {
        /**
         * 智能体usage构建器。
         * @return 智能体usage构建器的结果
         */
        return new AgentUsageBuilder();
    }

    /**
      * 智能体usage 建造器。
     * @author CH
     * @since 4.0.0
     */
    public static class AgentUsageBuilder {

        /**
          * 输入 令牌 数
         */
        private Integer promptTokens;

        /**
          * 输出 令牌 数
         */
        private Integer completionTokens;

        /**
          * 总 令牌 数
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
         * 提示符令牌
         *
         * @param promptTokens 提示符令牌
         * @return 提示符令牌的结果
         */
        public AgentUsageBuilder promptTokens(Integer promptTokens) { this.promptTokens = promptTokens; return this; }
        /**
         * 完成令牌
         *
         * @param completionTokens 完成令牌
         * @return 完成令牌的结果
         */
        public AgentUsageBuilder completionTokens(Integer completionTokens) { this.completionTokens = completionTokens; return this; }
        /**
         * 总计令牌
         *
         * @param totalTokens total令牌
         * @return total令牌的结果
         */
        public AgentUsageBuilder totalTokens(Integer totalTokens) { this.totalTokens = totalTokens; return this; }
        /**
         * Cost
         *
         * @param cost cost
         * @return cost的结果
         */
        public AgentUsageBuilder cost(Double cost) { this.cost = cost; return this; }
        /**
         * 模型
         *
         * @param model 模型
         * @return 模型的结果
         */
        public AgentUsageBuilder model(String model) { this.model = model; return this; }

        /**
          * 构造 智能体usage 实例。
         *
         * @return AgentUsage 实例
         */
        public AgentUsage build() {
            /**
             * 智能体usage。
             * @param promptTokens 提示符令牌
             * @param completionTokens 完成令牌
             * @param totalTokens total令牌
             * @param cost cost
             * @param model 模型
             * @return 智能体usage的结果
             */
            return new AgentUsage(promptTokens, completionTokens, totalTokens, cost, model);
        }
    }
}
