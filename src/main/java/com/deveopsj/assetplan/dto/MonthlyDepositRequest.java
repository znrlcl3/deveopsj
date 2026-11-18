package com.deveopsj.assetplan.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MonthlyDepositRequest {
    private LocalDate date;
    private List<Row> rows = new ArrayList<>();
    @Getter @Setter
    public static class Row {
        private boolean selected;
        private Long planId;
        private Long amount;
    }
}
