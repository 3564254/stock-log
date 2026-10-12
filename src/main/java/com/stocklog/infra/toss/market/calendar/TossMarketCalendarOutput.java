package com.stocklog.infra.toss.market.calendar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossMarketCalendarOutput(
        Day today,
        Day previousBusinessDay,
        Day nextBusinessDay
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Day(
            LocalDate date,
            Session dayMarket,
            Session preMarket,
            Session regularMarket,
            Session afterMarket,
            Integrated integrated
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Integrated(
            Session preMarket,
            Session regularMarket,
            Session afterMarket
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Session(
            OffsetDateTime startTime,
            OffsetDateTime endTime
    ) {
        /** start 포함, end 미포함 */
        public boolean contains(OffsetDateTime now) {
            return startTime != null && endTime != null
                    && !now.isBefore(startTime) && now.isBefore(endTime);
        }
    }
}
