package com.deveopsj.assetplan.service;

import com.deveopsj.assetplan.entity.AccountBalanceSnapshot;
import com.deveopsj.assetplan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.math.BigInteger;
import java.security.*;

@Service @RequiredArgsConstructor
public class BalanceSnapshotService {
    private final AccountBalanceSnapshotRepository snapshots;
    private final AssetPlanRepository plans;
    private final BalanceFileParser parser;
    private final ObjectMapper mapper;
    public record View(Long id,Long planId,String planName,LocalDate date,LocalDateTime importedAt,BalanceFileParser.Parsed data) {}
    public record Preview(Long planId,String planName,LocalDate date,String fileName,byte[] bytes,String digest,Long previousId,BalanceFileParser.Parsed data,View previous) {}
    public record Report(List<View> accounts) {
        public BigInteger total() { return accounts.stream().map(v->BigInteger.valueOf(v.data().total())).reduce(BigInteger.ZERO,BigInteger::add); }
    }
    @Transactional(readOnly=true)
    public Preview preview(Long planId,LocalDate date,byte[] bytes,String fileName,Long memberId) {
        var plan=plans.findByIdAndMemberMemberId(planId,memberId).orElseThrow(()->new IllegalArgumentException("본인의 계좌를 선택하세요."));
        if(date==null || date.isAfter(LocalDate.now()) || date.getYear()<1900) throw new IllegalArgumentException("잔고 기준일을 확인하세요. 미래 날짜는 등록할 수 없습니다.");
        var parsed=parser.parse(bytes);
        String digest=digest(bytes);
        if(snapshots.existsByPlanIdAndBalanceDateAndDigest(planId,date,digest)) throw new IllegalArgumentException("같은 계좌·기준일에 이미 등록한 파일입니다.");
        var previous=snapshots.findFirstByPlanIdOrderByBalanceDateDescIdDesc(planId).orElse(null);
        if(previous!=null && date.isBefore(previous.getBalanceDate())) throw new IllegalArgumentException("최신 잔고보다 이전 날짜입니다. 최신 기준일 이후의 파일을 등록하세요.");
        String safe=fileName==null?"balance.xls":fileName.replace('\\','/');safe=safe.substring(safe.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");
        if(safe.length()>200) safe=safe.substring(safe.length()-200);
        return new Preview(planId,plan.getPlanName(),date,safe,bytes.clone(),digest,previous==null?null:previous.getId(),parsed,previous==null?null:view(previous));
    }
    @Transactional
    public Long save(Preview preview,Long memberId,boolean confirmed) {
        var plan=plans.lockOwned(preview.planId(),memberId).orElseThrow(()->new IllegalArgumentException("본인의 계좌만 등록할 수 있습니다."));
        var latest=snapshots.findFirstByPlanIdOrderByBalanceDateDescIdDesc(plan.getId()).orElse(null);
        if(!Objects.equals(preview.previousId(),latest==null?null:latest.getId())) throw new IllegalArgumentException("잔고가 변경되었습니다. 다시 업로드해 확인하세요.");
        if(latest!=null && !confirmed) throw new IllegalArgumentException("기존 잔고와 변경 내용을 확인한 뒤 체크하세요.");
        if(snapshots.existsByPlanIdAndBalanceDateAndDigest(plan.getId(),preview.date(),preview.digest())) throw new IllegalArgumentException("이미 등록한 잔고입니다.");
        var s=new AccountBalanceSnapshot();s.setPlan(plan);s.setBalanceDate(preview.date());s.setImportedAt(LocalDateTime.now());s.setFileName(preview.fileName());s.setDigest(preview.digest());s.setPayload(mapper.writeValueAsString(preview.data()));s.setOriginalFile(preview.bytes().clone());
        return snapshots.save(s).getId();
    }
    @Transactional(readOnly=true) public Report report(Long memberId) { return new Report(snapshots.latest(memberId).stream().map(this::view).toList()); }
    @Transactional(readOnly=true) public AccountBalanceSnapshot owned(Long id,Long memberId) { return snapshots.findByIdAndPlanMemberMemberId(id,memberId).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)); }
    private View view(AccountBalanceSnapshot s) { return new View(s.getId(),s.getPlan().getId(),s.getPlan().getPlanName(),s.getBalanceDate(),s.getImportedAt(),mapper.readValue(s.getPayload(),BalanceFileParser.Parsed.class)); }
    private String digest(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
}
