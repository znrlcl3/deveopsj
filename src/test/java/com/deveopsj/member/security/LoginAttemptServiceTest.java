package com.deveopsj.member.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LoginAttemptServiceTest {

    @Test
    void 스프링이_로그인차단_서비스와_필터를_생성한다() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "app.security.login.max-attempts=5",
                        "app.security.login.block-duration=10m")
                .withBean(LoginAttemptService.class)
                .withBean(LoginAttemptFilter.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LoginAttemptService.class);
                    assertThat(context).hasSingleBean(LoginAttemptFilter.class);
                });
    }

    @Test
    void 최대실패횟수에_도달하면_로그인을_차단한다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-31T00:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(3, Duration.ofMinutes(10), clock);

        service.loginFailed("user", "127.0.0.1");
        service.loginFailed("user", "127.0.0.1");
        assertThat(service.isBlocked("user", "127.0.0.1")).isFalse();

        service.loginFailed("user", "127.0.0.1");

        assertThat(service.isBlocked("USER", "127.0.0.1")).isTrue();
    }

    @Test
    void 로그인에_성공하면_실패기록을_초기화한다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-31T00:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(3, Duration.ofMinutes(10), clock);
        service.loginFailed("user", "127.0.0.1");
        service.loginFailed("user", "127.0.0.1");

        service.loginSucceeded("user", "127.0.0.1");
        service.loginFailed("user", "127.0.0.1");

        assertThat(service.isBlocked("user", "127.0.0.1")).isFalse();
    }

    @Test
    void 차단시간이_지나면_다시_로그인할_수_있다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-31T00:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(3, Duration.ofMinutes(10), clock);
        service.loginFailed("user", "127.0.0.1");
        service.loginFailed("user", "127.0.0.1");
        service.loginFailed("user", "127.0.0.1");

        clock.advance(Duration.ofMinutes(10));

        assertThat(service.isBlocked("user", "127.0.0.1")).isFalse();
    }

    @Test
    void 실패횟수도_차단시간이_지나면_초기화된다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-31T00:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(3, Duration.ofMinutes(10), clock);
        service.loginFailed("user", "127.0.0.1");
        service.loginFailed("user", "127.0.0.1");
        clock.advance(Duration.ofMinutes(10));

        assertThat(service.isBlocked("user", "127.0.0.1")).isFalse();
        service.loginFailed("user", "127.0.0.1");

        assertThat(service.isBlocked("user", "127.0.0.1")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
