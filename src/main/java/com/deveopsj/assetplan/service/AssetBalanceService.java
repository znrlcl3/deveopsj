package com.deveopsj.assetplan.service;

import com.deveopsj.assetplan.dto.AssetBalanceRequest;
import com.deveopsj.assetplan.entity.AssetBalance;
import com.deveopsj.assetplan.repository.AssetBalanceRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.util.List;
import java.util.Objects;

@Service @Validated @RequiredArgsConstructor @Transactional(readOnly = true)
public class AssetBalanceService {
    private final AssetBalanceRepository repository;
    public List<AssetBalance> list(Long memberId) { return repository.findByMemberIdOrderByIdDesc(memberId); }
    public AssetBalance get(Long id, Long memberId) {
        return repository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new IllegalArgumentException("자산을 찾을 수 없습니다."));
    }
    @Transactional
    public void save(@Valid AssetBalanceRequest request, Long memberId) {
        AssetBalance balance = request.getId() == null ? new AssetBalance() : get(request.getId(), memberId);
        if (request.getId() != null && !Objects.equals(balance.getVersion(), request.getVersion()))
            throw new IllegalArgumentException("다른 화면에서 수정된 자산입니다. 새로고침 후 다시 확인해 주세요.");
        balance.setMemberId(memberId);
        balance.setName(request.getName().strip());
        balance.setKind(request.getKind());
        balance.setAmount(request.getAmount());
        balance.setAsOfDate(request.getAsOfDate());
        balance.setMaturityDate(request.getKind() == AssetBalance.Kind.CASH ? null : request.getMaturityDate());
        repository.saveAndFlush(balance);
    }
    @Transactional
    public void delete(Long id, Long version, Long memberId) {
        var balance = get(id, memberId);
        if (!Objects.equals(balance.getVersion(), version))
            throw new IllegalArgumentException("수정된 자산입니다. 새로고침 후 다시 확인해 주세요.");
        repository.delete(balance);
        repository.flush();
    }
}
