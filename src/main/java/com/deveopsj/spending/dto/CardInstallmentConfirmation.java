package com.deveopsj.spending.dto;

import java.time.LocalDate;

public record CardInstallmentConfirmation(LocalDate firstPaymentDate, boolean reviewed) {}
