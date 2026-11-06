package com.deveopsj.assetplan.dto;

import com.deveopsj.assetplan.entity.AssetBalance.Kind;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Getter @Setter
public class AssetBalanceRequest {
    private Long id;
    private Long version;
    @NotBlank @Size(max = 80) private String name;
    @NotNull private Kind kind;
    @NotNull @Min(0) @Max(999999999999999L) private Long amount;
    @NotNull @PastOrPresent @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate asOfDate = LocalDate.now();
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate maturityDate;
}
