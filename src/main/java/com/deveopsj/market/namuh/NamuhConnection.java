package com.deveopsj.market.namuh;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "namuh_connection") @Getter @Setter
public class NamuhConnection {
    @Id private Long memberId;
    @Column(nullable = false, length = 2048) private String encryptedKey;
    @Column(nullable = false, length = 2048) private String encryptedSecret;
    @Column(length = 4096) private String encryptedToken;
    private Instant tokenExpiresAt;
    @Version private long version;
}
