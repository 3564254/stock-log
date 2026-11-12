package com.stocklog.infra.gemini.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class GeminiRequest {
    private List<Content> contents;

    /** 응답 형식 등. 없으면 보내지 않는다 */
    private java.util.Map<String, Object> generationConfig;

    public GeminiRequest(List<Content> contents) {
        this.contents = contents;
    }

    @Data
    @AllArgsConstructor
    public static class Content {
        private List<Part> parts;
    }

    @Data
    @AllArgsConstructor
    public static class Part {
        private String text;
    }
}
