package com.chua.starter.ai.support.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = RagProperties.PRE)
public class RagProperties {

    public static final String PRE = "spring.ai.rag";

    private boolean enabled = true;
    private String embeddingEngine = "memory";
    private String vectorStorage = "jvector";
    private int chunkSize = 1000;
    private int chunkOverlap = 200;
    private int defaultTopK = 5;
    private double defaultSimilarityThreshold = 0.7;
    private String uploadDir = "./rag-uploads";
}
