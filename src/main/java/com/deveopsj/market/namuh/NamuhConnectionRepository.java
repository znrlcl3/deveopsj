package com.deveopsj.market.namuh;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NamuhConnectionRepository extends JpaRepository<NamuhConnection, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NamuhConnection n where n.memberId = :memberId")
    Optional<NamuhConnection> lockByMember(@Param("memberId") Long memberId);
}
