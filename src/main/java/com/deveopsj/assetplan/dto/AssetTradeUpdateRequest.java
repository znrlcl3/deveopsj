package com.deveopsj.assetplan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetTradeUpdateRequest extends AssetTradeSaveRequest {

    @NotNull(message = "수정할 매매 내역을 선택해 주세요.")
    private Long id;
}
