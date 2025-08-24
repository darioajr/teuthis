package com.github.darioajr.teuthis.security;

import java.time.Instant;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.github.darioajr.teuthis.infra.Config;

/**
 * JWT token validation and creation utilities
 */
public class JwtValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtValidator.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("security");
    
    private static final String JWT_SECRET = Config.str("teuthis.security.jwt.secret");
    private static final int JWT_EXPIRATION_HOURS = Config.i("teuthis.security.jwt.expiration.hours");
    private static final Algorithm algorithm = Algorithm.HMAC256(JWT_SECRET);
    private static final JWTVerifier verifier = JWT.require(algorithm).build();
    
    /**
     * Validate JWT token
     */
    public static boolean validate(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        
        try {
            DecodedJWT jwt = verifier.verify(token);
            
            // Additional validation
            if (jwt.getExpiresAt().before(new Date())) {
                securityLogger.warn("Expired JWT token attempted: {}", jwt.getSubject());
                return false;
            }
            
            logger.debug("✅ JWT token validated successfully for subject: {}", jwt.getSubject());
            return true;
            
        } catch (JWTVerificationException e) {
            securityLogger.warn("❌ Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract subject from JWT token
     */
    public static String getSubject(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getSubject();
        } catch (JWTVerificationException e) {
            logger.warn("⚠️ Could not extract subject from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract claim from JWT token
     */
    public static String getClaim(String token, String claimName) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getClaim(claimName).asString();
        } catch (JWTVerificationException e) {
            logger.warn("⚠️ Could not extract claim '{}' from token: {}", claimName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Create JWT token for testing/development
     */
    public static String createToken(String subject, String... roles) {
        try {
            Instant now = Instant.now();
            Instant expiration = now.plusSeconds(JWT_EXPIRATION_HOURS * 3600L);
            
            var builder = JWT.create()
                .withSubject(subject)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiration))
                .withIssuer("teuthis");
            
            // Add roles if provided
            if (roles.length > 0) {
                builder.withArrayClaim("roles", roles);
            }
            
            String token = builder.sign(algorithm);
            logger.info("✅ JWT token created for subject: {}", subject);
            return token;
            
        } catch (JWTCreationException e) {
            logger.error("❌ Error creating JWT token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create JWT token", e);
        }
    }
    
    /**
     * Check if authentication is enabled
     */
    public static boolean isAuthEnabled() {
        return Config.b("teuthis.security.auth.enabled");
    }
}
