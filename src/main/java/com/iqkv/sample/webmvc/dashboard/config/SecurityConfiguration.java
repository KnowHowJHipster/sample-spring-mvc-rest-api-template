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

package  com.iqkv.sample.webmvc.dashboard.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.iqkv.boot.security.AuthoritiesConstants;
import com.iqkv.boot.security.SecurityProperties;
import com.iqkv.sample.webmvc.dashboard.web.filter.SpaWebFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

  private final SecurityProperties securityProperties;

  public SecurityConfiguration(SecurityProperties securityProperties) {
    this.securityProperties = securityProperties;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, MvcRequestMatcher.Builder mvc) throws Exception {
    http
        .cors(withDefaults())
        .csrf(csrf -> csrf.disable())
        .addFilterAfter(new SpaWebFilter(), BasicAuthenticationFilter.class)
        .headers(headers ->
            headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(securityProperties.getContentSecurityPolicy()))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicyHeader(permissions ->
                    permissions.policy(
                        "camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()"
                    )
                )
        )
        .authorizeHttpRequests(authz ->
            // prettier-ignore
            authz
                .requestMatchers(mvc.pattern("/index.html"), mvc.pattern("/*.js"), mvc.pattern("/*.txt"), mvc.pattern("/*.json"), mvc.pattern("/*.map"), mvc.pattern("/*.css")).permitAll()
                .requestMatchers(mvc.pattern("/*.ico"), mvc.pattern("/*.png"), mvc.pattern("/*.svg"), mvc.pattern("/*.webapp")).permitAll()
                .requestMatchers(mvc.pattern("/app/**")).permitAll()
                .requestMatchers(mvc.pattern("/i18n/**")).permitAll()
                .requestMatchers(mvc.pattern("/content/**")).permitAll()
                .requestMatchers(mvc.pattern("/swagger-ui/**")).permitAll()
                .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/authenticate")).permitAll()
                .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/authenticate")).permitAll()
                .requestMatchers(mvc.pattern("/api/register")).permitAll()
                .requestMatchers(mvc.pattern("/api/activate")).permitAll()
                .requestMatchers(mvc.pattern("/api/account/reset-password/init")).permitAll()
                .requestMatchers(mvc.pattern("/api/account/reset-password/finish")).permitAll()
                .requestMatchers(mvc.pattern("/api/admin/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                .requestMatchers(mvc.pattern("/api/**")).authenticated()
                .requestMatchers(mvc.pattern("/v3/api-docs/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                .requestMatchers(mvc.pattern("/management/health")).permitAll()
                .requestMatchers(mvc.pattern("/management/health/**")).permitAll()
                .requestMatchers(mvc.pattern("/management/info")).permitAll()
                .requestMatchers(mvc.pattern("/management/prometheus")).permitAll()
                .requestMatchers(mvc.pattern("/management/**")).hasAuthority(AuthoritiesConstants.ADMIN)
        )
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exceptions ->
            exceptions
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
    return http.build();
  }

  @Bean
  MvcRequestMatcher.Builder mvc(HandlerMappingIntrospector introspector) {
    return new MvcRequestMatcher.Builder(introspector);
  }
}
