/*
 * Copyright 2025 IQKV Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iqkv.boot.security.jwt;

import static com.iqkv.boot.security.SecurityUtils.AUTHORITIES_KEY;
import static com.iqkv.boot.security.SecurityUtils.JWT_ALGORITHM;

import java.time.Instant;
import java.util.Collections;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.util.Base64;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

public class JwtAuthenticationTestUtils {

  public static final String BEARER = "Bearer ";

  @Bean
  private HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
    return new HandlerMappingIntrospector();
  }

  @Bean
  private MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  public static String createValidToken(String jwtKey) {
    return createValidTokenForUser(jwtKey, "anonymous");
  }

  public static String createValidTokenForUser(String jwtKey, String user) {
    JwtEncoder encoder = jwtEncoder(jwtKey);

    var now = Instant.now();

    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuedAt(now)
        .expiresAt(now.plusSeconds(60))
        .subject(user)
        .claims(customClaim -> customClaim.put(AUTHORITIES_KEY, Collections.singletonList("ROLE_ADMIN")))
        .build();

    JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();
    return encoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
  }

  public static String createTokenWithDifferentSignature() {
    JwtEncoder encoder = jwtEncoder("Xfd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8");

    var now = Instant.now();
    var past = now.plusSeconds(60);

    JwtClaimsSet claims = JwtClaimsSet.builder().issuedAt(now).expiresAt(past).subject("anonymous").build();

    JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();
    return encoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
  }

  public static String createExpiredToken(String jwtKey) {
    JwtEncoder encoder = jwtEncoder(jwtKey);

    var now = Instant.now();
    var past = now.minusSeconds(600);

    JwtClaimsSet claims = JwtClaimsSet.builder().issuedAt(past).expiresAt(past.plusSeconds(1)).subject("anonymous").build();

    JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();
    return encoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
  }

  public static String createInvalidToken(String jwtKey) {
    return createValidToken(jwtKey).substring(1);
  }

  public static String createSignedInvalidJwt(String jwtKey) throws Exception {
    return calculateHMAC("foo", jwtKey);
  }

  private static JwtEncoder jwtEncoder(String jwtKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(getSecretKey(jwtKey)));
  }

  private static SecretKey getSecretKey(String jwtKey) {
    byte[] keyBytes = Base64.from(jwtKey).decode();
    return new SecretKeySpec(keyBytes, 0, keyBytes.length, JWT_ALGORITHM.getName());
  }

  private static String calculateHMAC(String data, String key) throws Exception {
    SecretKeySpec secretKeySpec = new SecretKeySpec(Base64.from(key).decode(), "HmacSHA512");
    Mac mac = Mac.getInstance("HmacSHA512");
    mac.init(secretKeySpec);
    return String.copyValueOf(Hex.encode(mac.doFinal(data.getBytes())));
  }
}
