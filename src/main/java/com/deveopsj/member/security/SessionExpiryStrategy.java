package com.deveopsj.member.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.session.InvalidSessionStrategy;
import org.springframework.security.web.session.SimpleRedirectInvalidSessionStrategy;

/** Missing CSRF tokens alone do not establish that a session has expired. */
final class SessionExpiryStrategy implements InvalidSessionStrategy {
    private final InvalidSessionStrategy expiredSession =
            new SimpleRedirectInvalidSessionStrategy("/member/login?expired");

    @Override
    public void onInvalidSessionDetected(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        if (request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid()) {
            expiredSession.onInvalidSessionDetected(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
