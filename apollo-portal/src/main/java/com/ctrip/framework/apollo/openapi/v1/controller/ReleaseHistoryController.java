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
package com.ctrip.framework.apollo.openapi.v1.controller;


import com.ctrip.framework.apollo.portal.entity.bo.ReleaseHistoryBO;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.service.ReleaseHistoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("openapiReleaseHistoryController")
public class ReleaseHistoryController {

    private final ReleaseHistoryService releaseHistoryService;

    public ReleaseHistoryController(final ReleaseHistoryService releaseHistoryService) {
        this.releaseHistoryService = releaseHistoryService;
    }

    @GetMapping("/openapi/v1/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/releases/histories")
    public List<ReleaseHistoryBO> findReleaseHistoriesByNamespace(@PathVariable String appId,
                                                                  @PathVariable String env,
                                                                  @PathVariable String clusterName,
                                                                  @PathVariable String namespaceName,
                                                                  @RequestParam(value = "page", defaultValue = "0") int page,
                                                                  @RequestParam(value = "size", defaultValue = "10") int size) {

        System.out.printf("DEBUG: ReleaseHistoryController.findReleaseHistoriesByNamespace, appId=%s, env=%s, clusterName=%s, namespaceName=%s\n", appId, env, clusterName, namespaceName);
        return releaseHistoryService.findNamespaceReleaseHistory(appId, Env.valueOf(env), clusterName, namespaceName, page, size);
    }

}
