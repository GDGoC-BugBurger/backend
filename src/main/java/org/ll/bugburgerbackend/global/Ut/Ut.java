package org.ll.bugburgerbackend.global.Ut;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

@Slf4j
public class Ut {
    public static class str {
        public static boolean isBlank(String str) {
            return str == null || str.trim().isEmpty();
        }
    }

    public static class json {
        private static final ObjectMapper om = new ObjectMapper();

        @SneakyThrows
        public static String toString(Object obj) {
            return om.writeValueAsString(obj);
        }
    }

    public static class jwt {
        public static String toString(String secret, long expireSeconds, Map<String, Object> body) {
            Date issuedAt = new Date();
            Date expiration = new Date(issuedAt.getTime() + 1000L * expireSeconds);

            SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes());

            String jwt = Jwts.builder()
                    .claims(body)
                    .issuedAt(issuedAt)
                    .expiration(expiration)
                    .signWith(secretKey)
                    .compact();

            log.debug("Generated JWT: {}", jwt);
            return jwt;
        }

        public static boolean isValid(String secret, String jwtStr) {
            SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes());

            try {
                Claims claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(jwtStr)
                        .getPayload();

                Date expiration = claims.getExpiration();
                if (expiration.before(new Date())) {
                    log.error("JWT token has expired. Expiration: {}", expiration);
                    return false;
                }

                log.debug("JWT token is valid. Claims: {}", claims);
                return true;
            } catch (ExpiredJwtException e) {
                log.error("JWT token has expired", e);
                return false;
            } catch (MalformedJwtException e) {
                log.error("JWT token is malformed", e);
                return false;
            } catch (SignatureException e) {
                log.error("JWT signature validation failed", e);
                return false;
            } catch (Exception e) {
                log.error("JWT validation failed", e);
                return false;
            }
        }

        public static Map<String, Object> payload(String secret, String jwtStr) {
            SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes());

            try {
                Claims claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(jwtStr)
                        .getPayload();

                log.debug("JWT payload: {}", claims);
                return claims;
            } catch (Exception e) {
                log.error("Failed to parse JWT payload", e);
                return null;
            }
        }
    }
}