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
package com.ctrip.framework.apollo.portal.service;

import com.ctrip.framework.apollo.portal.entity.bo.ItemBO;
import com.ctrip.framework.apollo.portal.entity.bo.NamespaceBO;
import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.entity.vo.NamespaceRolesAssignedUsers;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.util.RoleUtils;
import org.springframework.stereotype.Service;
import com.ctrip.framework.apollo.portal.component.ReviewNotificationSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


@Service
public class NamespaceAuditService {
    private final NamespaceService namespaceService;
    private final ReviewNotificationSender reviewNotificationSender;
    private final RolePermissionService rolePermissionService;

    public NamespaceAuditService(NamespaceService namespaceService, ReviewNotificationSender reviewNotificationSender, RolePermissionService rolePermissionService) {
        this.namespaceService = namespaceService;
        this.reviewNotificationSender = reviewNotificationSender;
        this.rolePermissionService = rolePermissionService;
    }

    public void sendMsg(String appId, String env, String clusterName, String namespaceName) {
        List<ItemBO> changeItems = new ArrayList<>();
        Env e = Env.valueOf(env.toUpperCase());
        NamespaceBO namespaceBO = namespaceService.loadNamespaceBO(appId, e, clusterName, namespaceName);
        // 遍历 namespaceBO item，获取其中新旧值不同的 item
        for (ItemBO item : namespaceBO.getItems()) {
            System.out.printf("item: %s, oldValue: %s, newValue: %s \n", item.getItem().getKey(), item.getOldValue(), item.getNewValue());
            if (item.getOldValue() != null || item.getNewValue() != null) {
                if (item.getItem().getDataChangeLastModifiedBy() != null) {
                    changeItems.add(item);
                }
            }
        }
        if (changeItems.isEmpty()) {
            System.out.println("没有变更的配置项");
            return;
        }

        // 查找有发布权限的人
        NamespaceRolesAssignedUsers assignedUsers = new NamespaceRolesAssignedUsers();
        assignedUsers.setNamespaceName(namespaceName);
        assignedUsers.setAppId(appId);
        Set<UserInfo> releaseNamespaceUsers =
                rolePermissionService.queryUsersWithRole(RoleUtils.buildReleaseNamespaceRoleName(appId, namespaceName));
        assignedUsers.setReleaseRoleUsers(releaseNamespaceUsers);

        if (!releaseNamespaceUsers.isEmpty()) {
            for (UserInfo user : releaseNamespaceUsers) {
                System.out.printf("发布人 %s", user.getEmail());
            }
        }else {
            System.out.println("没有发布人");
            return;
        }

        // 发送通知
        reviewNotificationSender.sendReviewNotification(appId, clusterName, namespaceName, changeItems, releaseNamespaceUsers);

    }

}
