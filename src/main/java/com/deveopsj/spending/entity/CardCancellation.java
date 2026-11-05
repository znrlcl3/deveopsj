package com.deveopsj.spending.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "card_cancellation", uniqueConstraints = @UniqueConstraint(
        name = "uk_card_cancellation_member_key", columnNames = {"member_id", "transaction_key"}))
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class CardCancellation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "transaction_key", nullable = false, length = 64) private String transactionKey;
    @Column(nullable = false) private Long spendingId;
    @Column(nullable = false) private long refundAmount;
    @Column(nullable = false) private long beforeAmount;
    @Column(nullable = false) private long afterAmount;
    @Column(nullable = false) private LocalDate cancellationDate;
    @Column(nullable = false) private LocalDateTime appliedAt;
}
