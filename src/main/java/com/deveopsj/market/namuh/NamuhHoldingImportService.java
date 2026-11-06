package com.deveopsj.market.namuh;

import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import com.deveopsj.assetplan.dto.HoldingSaveRequest;
import com.deveopsj.assetplan.service.HoldingService;
import com.deveopsj.member.entity.Member;

@Service @RequiredArgsConstructor
public class NamuhHoldingImportService {
    private final NamuhStockResolver resolver;
    private final HoldingService holdings;
    private final Validator validator;
    public record Row(NamuhService.Balance balance, Long assetId, String matchedName, String reason) {
        public boolean eligible() { return assetId != null && reason.isEmpty(); }
    }
    public List<Row> preview(List<NamuhService.Balance> balances) {
        List<Row> result = new ArrayList<>();
        for (var balance : balances) {
            var resolution = resolver.resolve(balance.symbol(), balance.currency());
            if (resolution.asset() == null) {
                result.add(new Row(balance, null, "", resolution.error()));
                continue;
            }
            var asset = resolution.asset();
            long duplicateCount = balances.stream().filter(b -> b.symbol().equals(balance.symbol()) && b.currency().equals(balance.currency())).count();
            String reason = duplicateCount > 1 ? "같은 종목이 여러 유형으로 조회되었습니다. 합산 잔고를 확인해 직접 입력하세요." : "";
            var request = request(new Row(balance, asset.getId(), asset.getAssetName(), ""), 1L);
            if (!validator.validate(request).isEmpty() || balance.exchangeRate().signum() <= 0)
                reason = "수량·평균 매수가·매입 환율을 확인한 후 직접 입력하세요.";
            result.add(new Row(balance, asset.getId(), asset.getAssetName(), reason));
        }
        return List.copyOf(result);
    }
    @Transactional
    public void apply(Member member, Long planId, List<Row> rows, List<Integer> selected) {
        if (selected == null || selected.isEmpty() || new HashSet<>(selected).size() != selected.size())
            throw new IllegalArgumentException("반영할 종목을 선택하세요.");
        for (int index : selected) {
            if (index < 0 || index >= rows.size() || !rows.get(index).eligible())
                throw new IllegalArgumentException("반영할 수 없는 종목이 포함되었습니다. 다시 조회하세요.");
        }
        Set<Long> instruments = new HashSet<>();
        for (int index : selected) {
            var row = rows.get(index);
            if (!instruments.add(row.assetId())) throw new IllegalArgumentException("같은 종목의 중복 행을 함께 반영할 수 없습니다.");
            holdings.save(request(row, planId), member);
        }
    }
    private HoldingSaveRequest request(Row row, Long planId) {
        var request = new HoldingSaveRequest(); request.setAssetPlanId(planId); request.setInvestmentAssetId(row.assetId());
        request.setQuantity(row.balance().quantity());
        request.setAveragePrice(row.balance().averagePrice().setScale(4, RoundingMode.HALF_UP));
        request.setExchangeRate(row.balance().exchangeRate().setScale(6, RoundingMode.HALF_UP));
        return request;
    }
}
