package com.market.security.jwt;

import com.market.security.services.UserDetailsImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    private static final String VERSION_CLAIM = "ver";

    private static final String USER_ID_CLAIM = "uid";

    private static final String ROLES_CLAIM = "roles";

    private final SecretKey key;

    private final long jwtExpirationMs;

    public JwtUtils(@Value("${app.jwtSecret}") String jwtSecret,
                    @Value("${app.jwtExpirationMs}") long jwtExpirationMs) {
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32)
            throw new IllegalStateException("app.jwtSecret (JWT_SECRET) must be at least 32 bytes long");
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.jwtExpirationMs = jwtExpirationMs;
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        Date now = new Date();

        return Jwts.builder()
                .setSubject(userPrincipal.getUsername())
                .claim(VERSION_CLAIM, userPrincipal.getTokenVersion())
                .claim(USER_ID_CLAIM, userPrincipal.getId())
                .claim(ROLES_CLAIM, userPrincipal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).collect(Collectors.toList()))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + jwtExpirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Returns the token's claims, or null if the token is invalid or expired.
     */
    public Claims parseValidToken(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(key).build()
                    .parseClaimsJws(token).getBody();
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("Rejected JWT: {}", e.getMessage());
            return null;
        }
    }

    /** The token version the token was issued with, or -1 if it has none. */
    public static int tokenVersion(Claims claims) {
        Integer version = claims.get(VERSION_CLAIM, Integer.class);
        return version == null ? -1 : version;
    }

    /** The user id in the token, or null for tokens issued before it was included. */
    public static Long userId(Claims claims) {
        Number id = claims.get(USER_ID_CLAIM, Number.class);
        return id == null ? null : id.longValue();
    }

    public static List<SimpleGrantedAuthority> authorities(Claims claims) {
        List<?> roles = claims.get(ROLES_CLAIM, List.class);
        if (roles == null)
            return Collections.emptyList();
        return roles.stream().map(role -> new SimpleGrantedAuthority(String.valueOf(role))).collect(Collectors.toList());
    }
}
