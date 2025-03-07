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

package com.iqkv.sample.webmvc.dashboard.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

public class SpaWebFilter extends OncePerRequestFilter {

  /**
   * Forwards any unmapped paths (except those containing a period) to the client {@code index.html}.
   */
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // Request URI includes the contextPath if any, removed it.
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (
        !path.startsWith("/api")
        && !path.startsWith("/management")
        && !path.startsWith("/v3/api-docs")
        && !path.startsWith("/h2-console")
        && !path.contains(".")
        && path.matches("/(.*)")
    ) {
      request.getRequestDispatcher("/index.html").forward(request, response);
      return;
    }

    filterChain.doFilter(request, response);
  }
}
