package com.stocklog.domain.stock.analysis.domain.technical;

import lombok.*;
import java.util.List;

@Getter
@AllArgsConstructor(staticName = "of")
public class TechnicalScore {
    private final int score;
    private final List<String> signals;
}
