package com.deveopsj.assetplan.service;
import com.deveopsj.assetplan.entity.NamuhImportArchive;
import com.deveopsj.assetplan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
public class NamuhArchiveService {
    private final NamuhImportArchiveRepository archives;
    private final AssetPlanRepository plans;
    private final com.deveopsj.income.repository.IncomeRepository incomes;
    @Transactional public Long retain(byte[] bytes,String name,Long planId,Long memberId) {
        if(bytes==null || bytes.length==0 || bytes.length>NamuhTradeFileParser.MAX_BYTES) throw new IllegalArgumentException("5MB 이하의 파일을 선택하세요.");
        var member=incomes.lockOwner(memberId).orElseThrow(() -> new IllegalArgumentException("회원을 확인할 수 없습니다."));
        var plan=plans.findByIdAndMemberMemberId(planId,memberId).orElseThrow(() -> new IllegalArgumentException("본인의 플랜을 선택하세요."));
        String digest;
        try { digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        var previous=archives.findByMemberMemberIdAndAssetPlanIdAndDigest(memberId,planId,digest);
        if(previous.isPresent()) return previous.get().getId();
        String filename=name==null?"upload.bin":name.replace('\\','/');filename=filename.substring(filename.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","_");
        if(filename.isBlank()) filename="upload.bin";if(filename.length()>200) filename=filename.substring(filename.length()-200);
        var archive=new NamuhImportArchive();archive.setMember(member);archive.setAssetPlan(plan);archive.setFileName(filename);archive.setDigest(digest);archive.setSizeBytes(bytes.length);archive.setContent(bytes.clone());
        return archives.saveAndFlush(archive).getId();
    }
    @Transactional(readOnly=true) public List<NamuhImportArchiveRepository.Info> recent(Long memberId) { return archives.recent(memberId,org.springframework.data.domain.PageRequest.of(0,20)); }
    @Transactional(readOnly=true) public NamuhImportArchive owned(Long id,Long memberId) { return archives.findByIdAndMemberMemberId(id,memberId).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)); }
}
