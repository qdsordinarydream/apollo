/*
 * Copyright 2024 Apollo Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.ctrip.framework.apollo.portal.spi.mtgsso;

import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.entity.po.Authority;
import com.ctrip.framework.apollo.portal.entity.po.UserPO;
import com.ctrip.framework.apollo.portal.repository.AuthorityRepository;
import com.ctrip.framework.apollo.portal.repository.UserRepository;
import com.ctrip.framework.apollo.portal.spi.UserService;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * mtg sso 接入
 */
@Component
public class MTGSSOUserService implements UserService, UserDetailsService {

  private final PasswordEncoder passwordEncoder;

  private final UserRepository userRepository;

  private final AuthorityRepository authorityRepository;

  public MTGSSOUserService(
          PasswordEncoder passwordEncoder,
          UserRepository userRepository,
          AuthorityRepository authorityRepository) {
    this.passwordEncoder = passwordEncoder;
    this.userRepository = userRepository;
    this.authorityRepository = authorityRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    // 验证之后回调xx接口
    // xx接口写入登陆态
    // 刷新页面
    System.out.println("成功************");
    UserPO user = userRepository.findByUsername(username);
    if (user == null) {
      user = new UserPO();
      user.setPassword("configcenter_default");
      user.setUsername(username);
      user.setEmail(username);
      user.setUserDisplayName(username);
      user.setEnabled(1);
      this.create(user);

      // 重新查询
      user = userRepository.findByUsername(username);
      //throw new UsernameNotFoundException("User " + username + " was not found in the database");
      System.out.println("user 不存在，被创建");
    }

    return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPassword(),
            true,
            true,
            true,
            true,
            AuthorityUtils.createAuthorityList("ROLE_user")
    );
  };

  @Transactional
  public void create(UserPO user) {
    String username = user.getUsername();
    String newPassword = passwordEncoder.encode(user.getPassword());
    UserPO managedUser = userRepository.findByUsername(username);
    if (managedUser != null) {
      throw BadRequestException.userAlreadyExists(username);
    }
    //create
    user.setPassword(newPassword);
    user.setEnabled(user.getEnabled());
    userRepository.save(user);

    //save authorities
    Authority authority = new Authority();
    authority.setUsername(username);
    authority.setAuthority("ROLE_user");
    authorityRepository.save(authority);
  }

  @Transactional
  public void update(UserPO user) {
    String username = user.getUsername();
    String newPassword = passwordEncoder.encode(user.getPassword());
    UserPO managedUser = userRepository.findByUsername(username);
    if (managedUser == null) {
      throw BadRequestException.userNotExists(username);
    }
    managedUser.setPassword(newPassword);
    managedUser.setEmail(user.getEmail());
    managedUser.setUserDisplayName(user.getUserDisplayName());
    managedUser.setEnabled(user.getEnabled());
    userRepository.save(managedUser);
  }

  @Transactional
  public void changeEnabled(UserPO user) {
    String username = user.getUsername();
    UserPO managedUser = userRepository.findByUsername(username);
    managedUser.setEnabled(user.getEnabled());
    userRepository.save(managedUser);
  }

  @Override
  public List<UserInfo> searchUsers(String keyword, int offset, int limit,
      boolean includeInactiveUsers) {
    List<UserPO> users = this.findUsers(keyword, includeInactiveUsers);
    if (CollectionUtils.isEmpty(users)) {
      return Collections.emptyList();
    }
    return users.stream().map(UserPO::toUserInfo)
        .collect(Collectors.toList());
  }

  private List<UserPO> findUsers(String keyword, boolean includeInactiveUsers) {
    Map<Long, UserPO> users = new HashMap<>();
    List<UserPO> byUsername;
    List<UserPO> byUserDisplayName;
    if (includeInactiveUsers) {
      if (StringUtils.isEmpty(keyword)) {
        return (List<UserPO>) userRepository.findAll();
      }
      byUsername = userRepository.findByUsernameLike("%" + keyword + "%");
      byUserDisplayName = userRepository.findByUserDisplayNameLike("%" + keyword + "%");
    } else {
      if (StringUtils.isEmpty(keyword)) {
        return userRepository.findFirst20ByEnabled(1);
      }
      byUsername = userRepository.findByUsernameLikeAndEnabled("%" + keyword + "%", 1);
      byUserDisplayName = userRepository
          .findByUserDisplayNameLikeAndEnabled("%" + keyword + "%", 1);
    }
    if (!CollectionUtils.isEmpty(byUsername)) {
      for (UserPO user : byUsername) {
        users.put(user.getId(), user);
      }
    }
    if (!CollectionUtils.isEmpty(byUserDisplayName)) {
      for (UserPO user : byUserDisplayName) {
        users.put(user.getId(), user);
      }
    }
    return new ArrayList<>(users.values());
  }

  @Override
  public UserInfo findByUserId(String userId) {
    UserPO userPO = userRepository.findByUsername(userId);
    return userPO == null ? null : userPO.toUserInfo();
  }

  @Override
  public List<UserInfo> findByUserIds(List<String> userIds) {
    List<UserPO> users = userRepository.findByUsernameIn(userIds);

    if (CollectionUtils.isEmpty(users)) {
      return Collections.emptyList();
    }

    return users.stream().map(UserPO::toUserInfo).collect(Collectors.toList());
  }
}
