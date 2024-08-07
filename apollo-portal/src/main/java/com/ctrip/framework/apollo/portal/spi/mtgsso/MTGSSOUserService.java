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

import com.ctrip.framework.apollo.common.dto.MaxConnectDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.portal.component.RetryableRestTemplate;
import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.entity.po.Authority;
import com.ctrip.framework.apollo.portal.entity.po.UserPO;
import com.ctrip.framework.apollo.portal.repository.AuthorityRepository;
import com.ctrip.framework.apollo.portal.repository.UserRepository;
import com.ctrip.framework.apollo.portal.spi.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * mtg sso 接入
 */
@Component
public class MTGSSOUserService implements UserService, UserDetailsService {
  private Logger logger = LoggerFactory.getLogger(RetryableRestTemplate.class);

  private final PasswordEncoder passwordEncoder;

  private final UserRepository userRepository;

  private final AuthorityRepository authorityRepository;

  private final String connectAppKey = "e0046a3ed06ea274b184e9c6a2c8253d";
  private final String connectAppSecret = "JaxvTxDVPqOWCrtSfNKgyQ";
  private final String connectTokenUri = "https://connect.spotmaxtech.com/api/v1/connect/token";
  private final String connectUserInfoUri = "https://connect.spotmaxtech.com/api/v1/connect/user_info";

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
    System.out.println("************ code: " + username);

    String userInfo = getUserInfo(username);
    if (Objects.equals(userInfo, "")) {
      throw new UsernameNotFoundException("User " + username + " was not found in the database");
    }

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

  public String getUserInfo(String username) {
    String token = getMaxConnectToken(username);
    if (token == null || token.equals("")) {
      return "";
    }
    return getUserInfoByToken(token);
  }

  public String getUserInfoByToken(String token) {
    Map<String, String> signMap = new HashMap<>();
    long timestamp = System.currentTimeMillis()/1000;
    signMap.put("appkey", connectAppKey);
    signMap.put("access_token", token);
    signMap.put("timestamp", Long.toString(timestamp));
    String sign = generateSignature(signMap, connectAppSecret);
    
    try {
      String params = String.format("appkey=%s&access_token=%s&timestamp=%s&sign=%s",
              connectAppKey, token, timestamp, sign);

      URL url = new URL(connectUserInfoUri +"?"+params);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();

      connection.setRequestMethod("GET");
      int responseCode = connection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_OK) {
        ObjectMapper objectMapper = new ObjectMapper();
        MaxConnectDTO.UserInfo responseObj = objectMapper.readValue(connection.getInputStream(),  MaxConnectDTO.UserInfo.class);
        String email = responseObj.getThird_party_info().getProfiles().getEmail();
        if (!Objects.equals(email, "")) {
          return email;
        }

        return "";
      } else {
        logger.error("GET请求未成功, url: " + connectTokenUri +"?"+params + ", responseCode: " + responseCode);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return "";
  }

  public String getMaxConnectToken(String username) {
    Map<String, String> signMap = new HashMap<>();
    long timestamp = System.currentTimeMillis()/1000;
    signMap.put("appkey", connectAppKey);
    signMap.put("code", username);
    signMap.put("timestamp", Long.toString(timestamp));
    String sign = generateSignature(signMap, connectAppSecret);
    try {
      String params = String.format("appkey=%s&code=%s&timestamp=%s&sign=%s",
              connectAppKey, username, timestamp, sign);

      URL url = new URL(connectTokenUri +"?"+params);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();

      connection.setRequestMethod("GET");
      int responseCode = connection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_OK) {
        ObjectMapper objectMapper = new ObjectMapper();

        MaxConnectDTO.MaxConnectTokenDTO responseObj = objectMapper.readValue(connection.getInputStream(),  MaxConnectDTO.MaxConnectTokenDTO.class);
        if (!Objects.equals(responseObj.getAccess_token(), "")) {
          return responseObj.getAccess_token();
        }

        return "";
      } else {
        logger.error("GET请求未成功, url: " + connectTokenUri +"?"+params + ", responseCode: " + responseCode);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return "";
  }

  // 生成对应的签名
  public static String generateSignature(Map<String, String> params, String secret) {
    List<String> keys = new ArrayList<>(params.keySet());
    Collections.sort(keys);

    StringBuilder kvList = new StringBuilder();
    for (String key : keys) {
      kvList.append(key).append("=").append(params.get(key));
    }
    kvList.append(secret);

    return md5(kvList.toString());
  }

  private static String md5(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] messageDigest = md.digest(input.getBytes());
      StringBuilder sb = new StringBuilder();
      for (byte b : messageDigest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

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
