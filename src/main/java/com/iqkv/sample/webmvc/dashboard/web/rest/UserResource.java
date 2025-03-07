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

package com.iqkv.sample.webmvc.dashboard.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.iqkv.boot.info.ClientApplicationProperties;
import com.iqkv.boot.mvc.rest.HeaderUtil;
import com.iqkv.boot.mvc.rest.PaginationUtil;
import com.iqkv.boot.mvc.rest.ResponseUtil;
import com.iqkv.boot.security.AuthoritiesConstants;
import com.iqkv.boot.security.errors.BadRequestAlertException;
import com.iqkv.boot.security.errors.EmailAlreadyUsedException;
import com.iqkv.boot.security.errors.LoginAlreadyUsedException;
import com.iqkv.sample.webmvc.dashboard.config.Constants;
import com.iqkv.sample.webmvc.dashboard.domain.User;
import com.iqkv.sample.webmvc.dashboard.repository.UserRepository;
import com.iqkv.sample.webmvc.dashboard.service.MailService;
import com.iqkv.sample.webmvc.dashboard.service.UserService;
import com.iqkv.sample.webmvc.dashboard.service.dto.AdminUserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST controller for managing users.
 *
 * <p>This class accesses the {@link com.iqkv.sample.webmvc.dashboard.domain.User} entity, and needs to fetch its collection of authorities.
 *
 * <p>For a normal use-case, it would be better to have an eager relationship between User and Authority,
 * and send everything to the client side: there would be no View Model and DTO, a lot less code, and an outer-join
 * which would be good for performance.
 *
 * <p>We use a View Model and a DTO for 3 reasons:
 * <ul>
 * <li>We want to keep a lazy association between the user and the authorities, because people will
 * quite often do relationships with the user, and we don't want them to get the authorities all
 * the time for nothing (for performance reasons). This is the #1 goal: we should not impact our users'
 * application because of this use-case.</li>
 * <li> Not having an outer join causes n+1 requests to the database. This is not a real issue as
 * we have by default a second-level cache. This means on the first HTTP call we do the n+1 requests,
 * but then all authorities come from the cache, so in fact it's much better than doing an outer join
 * (which will get lots of data from the database, for each HTTP call).</li>
 * <li> As this manages users, for security reasons, we'd rather have a DTO layer.</li>
 * </ul>
 *
 * <p>Another option would be to have a specific JPA entity graph to handle this case.
 */
@RestController
@RequestMapping("/api/admin")
public class UserResource {

  private static final List<String> ALLOWED_ORDERED_PROPERTIES = Collections.unmodifiableList(
      Arrays.asList(
          "id",
          "login",
          "firstName",
          "lastName",
          "email",
          "activated",
          "langKey",
          "createdBy",
          "createdDate",
          "lastModifiedBy",
          "lastModifiedDate"
      )
  );

  private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);

  private final ClientApplicationProperties clientApplicationProperties;

  private final UserService userService;

  private final UserRepository userRepository;

  private final MailService mailService;

  public UserResource(ClientApplicationProperties clientApplicationProperties, UserService userService, UserRepository userRepository, MailService mailService) {
    this.clientApplicationProperties = clientApplicationProperties;
    this.userService = userService;
    this.userRepository = userRepository;
    this.mailService = mailService;
  }

  /**
   * {@code POST  /admin/users}  : Creates a new user.
   *
   * <p>Creates a new user if the login and email are not already used, and sends an
   * mail with an activation link.
   * The user needs to be activated on creation.
   *
   * @param userDTO the user to create.
   * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new user, or with status {@code 400 (Bad Request)} if the login or email is already in use.
   * @throws URISyntaxException       if the Location URI syntax is incorrect.
   * @throws BadRequestAlertException {@code 400 (Bad Request)} if the login or email is already in use.
   */
  @PostMapping("/users")
  @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
  public ResponseEntity<User> createUser(@Valid @RequestBody AdminUserDTO userDTO) throws URISyntaxException {
    LOG.debug("REST request to save User : {}", userDTO);

    if (userDTO.getId() != null) {
      throw new BadRequestAlertException("A new user cannot already have an ID", "userManagement", "idexists");
      // Lowercase the user login before comparing with database
    } else if (userRepository.findOneByLogin(userDTO.getLogin().toLowerCase()).isPresent()) {
      throw new LoginAlreadyUsedException();
    } else if (userRepository.findOneByEmailIgnoreCase(userDTO.getEmail()).isPresent()) {
      throw new EmailAlreadyUsedException();
    } else {
      User newUser = userService.createUser(userDTO);
      mailService.sendCreationEmail(newUser);
      return ResponseEntity.created(new URI("/api/admin/users/" + newUser.getLogin()))
          .headers(HeaderUtil.createAlert(clientApplicationProperties.getName(), "userManagement.created", newUser.getLogin()))
          .body(newUser);
    }
  }

  /**
   * {@code PUT /admin/users} : Updates an existing User.
   *
   * @param userDTO the user to update.
   * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated user.
   * @throws EmailAlreadyUsedException {@code 400 (Bad Request)} if the email is already in use.
   * @throws LoginAlreadyUsedException {@code 400 (Bad Request)} if the login is already in use.
   */
  @PutMapping({"/users", "/users/{login}"})
  @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
  public ResponseEntity<AdminUserDTO> updateUser(
      @PathVariable(name = "login", required = false) @Pattern(regexp = Constants.LOGIN_REGEX) String login,
      @Valid @RequestBody AdminUserDTO userDTO
  ) {
    LOG.debug("REST request to update User : {}", userDTO);
    Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(userDTO.getEmail());
    if (existingUser.isPresent() && (!existingUser.orElseThrow().getId().equals(userDTO.getId()))) {
      throw new EmailAlreadyUsedException();
    }
    existingUser = userRepository.findOneByLogin(userDTO.getLogin().toLowerCase());
    if (existingUser.isPresent() && (!existingUser.orElseThrow().getId().equals(userDTO.getId()))) {
      throw new LoginAlreadyUsedException();
    }
    Optional<AdminUserDTO> updatedUser = userService.updateUser(userDTO);

    return ResponseUtil.wrapOrNotFound(
        updatedUser,
        HeaderUtil.createAlert(clientApplicationProperties.getName(), "userManagement.updated", userDTO.getLogin())
    );
  }

  /**
   * {@code GET /admin/users} : get all users with all the details - calling this are only allowed for the administrators.
   *
   * @param pageable the pagination information.
   * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body all users.
   */
  @GetMapping("/users")
  @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
  public ResponseEntity<List<AdminUserDTO>> getAllUsers(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
    LOG.debug("REST request to get all User for an admin");
    if (!onlyContainsAllowedProperties(pageable)) {
      return ResponseEntity.badRequest().build();
    }

    final Page<AdminUserDTO> page = userService.getAllManagedUsers(pageable);
    HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
    return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
  }

  private boolean onlyContainsAllowedProperties(Pageable pageable) {
    return pageable.getSort().stream().map(Sort.Order::getProperty).allMatch(ALLOWED_ORDERED_PROPERTIES::contains);
  }

  /**
   * {@code GET /admin/users/:login} : get the "login" user.
   *
   * @param login the login of the user to find.
   * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the "login" user, or with status {@code 404 (Not Found)}.
   */
  @GetMapping("/users/{login}")
  @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
  public ResponseEntity<AdminUserDTO> getUser(@PathVariable("login") @Pattern(regexp = Constants.LOGIN_REGEX) String login) {
    LOG.debug("REST request to get User : {}", login);
    return ResponseUtil.wrapOrNotFound(userService.getUserWithAuthoritiesByLogin(login).map(AdminUserDTO::new));
  }

  /**
   * {@code DELETE /admin/users/:login} : delete the "login" User.
   *
   * @param login the login of the user to delete.
   * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
   */
  @DeleteMapping("/users/{login}")
  @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
  public ResponseEntity<Void> deleteUser(@PathVariable("login") @Pattern(regexp = Constants.LOGIN_REGEX) String login) {
    LOG.debug("REST request to delete User: {}", login);
    userService.deleteUser(login);
    return ResponseEntity.noContent().headers(HeaderUtil.createAlert(clientApplicationProperties.getName(), "userManagement.deleted", login)).build();
  }
}
