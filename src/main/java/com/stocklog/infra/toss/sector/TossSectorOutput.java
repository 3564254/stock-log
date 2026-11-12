package com.stocklog.infra.toss.sector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 토스 섹터 목록 응답 result */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossSectorOutput(List<Sector> sectors) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sector(
            String sectorId,
            String name,
            Integer depth,
            String parentSectorId
    ) {
    }
}
