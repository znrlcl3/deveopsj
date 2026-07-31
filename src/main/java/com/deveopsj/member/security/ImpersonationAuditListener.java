package com.deveopsj.member.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.web.authentication.switchuser.AuthenticationSwitchUserEvent;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class ImpersonationAuditListener {

    private static final Logger log = LoggerFactory.getLogger(ImpersonationAuditListener.class);

    @EventListener
    public void onSwitchUser(AuthenticationSwitchUserEvent event) {
        String administrator = event.getAuthentication().getAuthorities().stream()
                .filter(SwitchUserGrantedAuthority.class::isInstance)
                .map(SwitchUserGrantedAuthority.class::cast)
                .map(authority -> authority.getSource().getName())
                .findFirst()
                .orElse(event.getAuthentication().getName());
        log.info("ADMIN_IMPERSONATION administrator={} target={}",
                administrator, event.getTargetUser().getUsername());
    }
}
