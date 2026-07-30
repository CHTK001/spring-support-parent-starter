package com.chua.common.support.text.splitter;

import java.util.ArrayList;
import java.util.List;

public class RecursiveCharacterTextSplitter implements TextSplitter {

    private final int chunkSize;
    private final int chunkOverlap;

    public RecursiveCharacterTextSplitter(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    @Override
    public List<String> split(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize - chunkOverlap;
            if (start >= text.length()) break;
            if (end >= text.length()) break;
        }
        return chunks;
    }
}
