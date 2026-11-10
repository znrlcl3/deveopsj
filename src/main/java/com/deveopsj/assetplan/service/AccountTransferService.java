package com.deveopsj.assetplan.service;
import com.deveopsj.assetplan.entity.AccountTransfer;
import com.deveopsj.assetplan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.YearMonth;
import java.util.List;
@Service @RequiredArgsConstructor
public class AccountTransferService {
    private final AccountTransferRepository transfers;
    private final com.deveopsj.income.repository.IncomeRepository incomes;
    @Transactional(readOnly=true)
    public List<AccountTransfer> list(Long memberId, YearMonth month) {
        return transfers.findByAssetPlanMemberMemberIdAndTransferDateBetweenOrderByTransferDateDescIdDesc(memberId,month.atDay(1),month.atEndOfMonth());
    }
    @Transactional
    public void delete(Long id,Long memberId) {
        incomes.lockOwner(memberId).orElseThrow(() -> new IllegalArgumentException("회원을 확인할 수 없습니다."));
        var row=transfers.findByIdAndAssetPlanMemberMemberId(id,memberId).orElseThrow(() -> new IllegalArgumentException("입출금 내역을 찾을 수 없습니다."));
        transfers.delete(row);
    }
}
