package com.deveopsj.market.dto;

import java.time.LocalDate;
import java.util.List;

public record KrxStockSnapshot(LocalDate baseDate, List<KrxStockItem> stocks) {
}
