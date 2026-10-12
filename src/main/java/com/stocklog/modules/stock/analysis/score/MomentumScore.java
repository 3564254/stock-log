package com.stocklog.modules.stock.analysis.score;

import lombok.*;
import java.util.List;

@Getter
@AllArgsConstructor(staticName = "of")
public class MomentumScore {
    private final int score;
    private final List<String> signals;
}
