package com.deveopsj.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class MasterCodeDto {
    private String groupId;
    private String codeId; 
    private String codeName; 
    
    public static MasterCodeDto fromEntity(com.deveopsj.common.entity.MasterCode entity) {
        return MasterCodeDto.builder()
                .groupId(entity.getGroupId())
                .codeId(entity.getCodeId())
                .codeName(entity.getCodeName())
                .build();
    }
}