package com.chua.common.support.ai.agent;

public class AgentUsage {

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Double cost;
    private String model;

    public AgentUsage() {
    }

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

    public static AgentUsageBuilder builder() {
        return new AgentUsageBuilder();
    }

    public static class AgentUsageBuilder {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private Double cost;
        private String model;

        public AgentUsageBuilder promptTokens(Integer promptTokens) { this.promptTokens = promptTokens; return this; }
        public AgentUsageBuilder completionTokens(Integer completionTokens) { this.completionTokens = completionTokens; return this; }
        public AgentUsageBuilder totalTokens(Integer totalTokens) { this.totalTokens = totalTokens; return this; }
        public AgentUsageBuilder cost(Double cost) { this.cost = cost; return this; }
        public AgentUsageBuilder model(String model) { this.model = model; return this; }

        public AgentUsage build() {
            return new AgentUsage(promptTokens, completionTokens, totalTokens, cost, model);
        }
    }
}
