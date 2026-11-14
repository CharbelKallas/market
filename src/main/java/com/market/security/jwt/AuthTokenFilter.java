package com.market.security.jwt;

import com.market.repository.UserRepository;
import com.market.security.services.UserDetailsImpl;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

/**
 * Authenticates requests from the JWT. The token carries the user id and roles, so each request needs only one
 * small query: the user's current token version, which rejects tokens issued before the last password change.
 * Tokens are only ever issued to verified users. Any future change to a user's roles must bump the token version.
 */
@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    private final UserRepository userRepository;

    public AuthTokenFilter(JwtUtils jwtUtils, UserRepository userRepository) {
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String jwt = parseJwt(request);
        Claims claims = jwt == null ? null : jwtUtils.parseValidToken(jwt);
        Long userId = claims == null ? null : JwtUtils.userId(claims);

        if (userId != null) {
            Integer currentVersion = userRepository.findTokenVersionById(userId).orElse(null);
            if (Objects.equals(currentVersion, JwtUtils.tokenVersion(claims))) {
                UserDetailsImpl principal = new UserDetailsImpl(userId, claims.getSubject(), null, null, true,
                        currentVersion, JwtUtils.authorities(claims));
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}
