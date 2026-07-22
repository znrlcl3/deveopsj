package com.deveopsj.assetplan.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetSavingsSaveRequest {

    private Long assetPlanId;
    private Long amount;
    private LocalDate depositDate;
    private String memo;
}
