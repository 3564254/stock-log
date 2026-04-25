package com.stocklog.domain.stock.analysis.domain.valuation;

import lombok.*;
import java.util.List;

@Getter
@AllArgsConstructor(staticName = "of")
public class ValuationScore {
    private final int score;
    private final List<String> signals;
}