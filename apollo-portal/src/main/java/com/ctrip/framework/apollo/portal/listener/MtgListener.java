package com.ctrip.framework.apollo.portal.listener;

import com.ctrip.framework.apollo.common.dto.EhrMessageDTO;
import com.ctrip.framework.apollo.portal.api.API;
import com.ctrip.framework.apollo.portal.entity.bo.ItemBO;
import com.ctrip.framework.apollo.portal.entity.bo.ReleaseHistoryBO;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiRobotSendRequest;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taobao.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MtgListener extends API {
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @Value("${ddUrl}")
    private String ddUrl;

    @Value("${ddToken}")
    private String ddToken;

    @Value("${reviewHost}")
    private String reviewHost;

    @Value("${ddPicture}")
    private String pictureUrl;

    @Value("${ehrClientId}")
    private String ehrClientId;

    // 发送 POST 请求到钉钉机器人
    public void sendDingTalkMessage(ReleaseHistoryBO releaseHistory, ConfigPublishEvent.ConfigPublishInfo publishInfo) {
        System.out.println("send to dingding");
        System.out.printf("url: %s, token: %s, reviewUrl: %s \n", ddUrl, ddToken, reviewHost);

        if (publishInfo.getChangeItems().isEmpty()) {
            System.out.println("没有变更的配置项");
            return;
        }
        Map<String, String> ddUserIds = new HashMap<>();

        // 如果是回滚事件，发布是 configGroup 维度，所以只能是操作人一个人，不存在多人修改的情况
        if (publishInfo.isRollbackEvent()) {
            ddUserIds.put(getDDUserId(releaseHistory.getOperator()), "");
        } else {
            for (ItemBO entry : publishInfo.getChangeItems()) {
//                System.out.printf("收到的变更 key: %s, old: %s, value: %s, operator: %s \n", entry.getItem().getKey(), entry.getOldValue(), entry.getNewValue(), entry.getItem().getDataChangeLastModifiedBy());
                if (entry.getItem().getDataChangeLastModifiedBy() != null) {
                    ddUserIds.put(getDDUserId(entry.getItem().getDataChangeLastModifiedBy()), "");
                } else {
                    System.out.printf("收到的变更 key: %s, old: %s, value: %s 没有操作人 \n", entry.getItem().getKey(), entry.getOldValue(), entry.getNewValue());
                }
            }
        }

        System.out.printf("ddUserId: %s \n", ddUserIds);

        try {
            sendMsg(releaseHistory, publishInfo, ddUserIds);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("send to dingding success");
    }


    // 发送钉钉机器人群通知
    // https://open.dingtalk.com/document/robots/custom-robot-access
    private void sendMsg(ReleaseHistoryBO releaseHistory, ConfigPublishEvent.ConfigPublishInfo publishInfo, Map<String, String> dd) throws Exception {
        List<String> operators = new ArrayList<>(dd.keySet());
        if (publishInfo.getChangeItems() == null) {
            publishInfo.setChangeItems(new ArrayList<>());
        }

        DingTalkClient client = new DefaultDingTalkClient(ddUrl + ddToken);
        OapiRobotSendRequest req = new OapiRobotSendRequest();
        // 设置对应类型
        req.setMsgtype("markdown");
        // 设置标题
        OapiRobotSendRequest.Markdown markdown = new OapiRobotSendRequest.Markdown();
        String tps = getTypeString(publishInfo.isRollbackEvent());
        markdown.setTitle(tps);
        // 设置内容
        markdown.setText(createMarkdownContent(releaseHistory, publishInfo, operators));
        req.setMarkdown(markdown);
        // 设置@人
        OapiRobotSendRequest.At at = new OapiRobotSendRequest.At();
        at.setAtUserIds(operators);
        req.setAt(at);

        // 发送请求
        OapiRobotSendResponse response = client.execute(req);

        System.out.println(response.getBody());
    }

    private String createMarkdownContent(ReleaseHistoryBO releaseHistory, ConfigPublishEvent.ConfigPublishInfo publishInfo, List<String> operators) {
        return "#### **" + getTypeString(publishInfo.isRollbackEvent()) + "** \n " +
                "#### ***操作人***: [" + getOperator(operators) + "] \n " +
                "#### ***发布人***: [" + releaseHistory.getOperator() + "] \n " +
                "#### ***Appid***: [" + releaseHistory.getAppId() + "] \n " +
                "#### ***Cluster***: [" + releaseHistory.getClusterName() + "] \n " +
                "#### ***ConfigGroup***: [" + releaseHistory.getNamespaceName() + "] \n " +
                "#### ***变更内容***: " + publishInfo.getChangeItems().toString() + " \n " +
                "> ![screenshot](" + pictureUrl + ") \n " +
                "> ###### 详情信息 => [地址](" + getUrl(releaseHistory.getAppId(), releaseHistory.getClusterName()) + ") \n";
    }

    private String getOperator(List<String> dds) {
        // 设置接收者，多个用,分开
        StringJoiner joiner = new StringJoiner(",");
        for (String value : dds) {
            joiner.add("@" + value);
        }
        return joiner.toString();
    }

    private String getUrl(String appid, String cluster) {
        if (!Objects.equals(cluster, "default")) {
            return "http://" + reviewHost + "/config.html?#/appid=" + appid + "&cluster=" + cluster;
        }
        return "http://" + reviewHost + "/config.html?#/appid=" + appid;
    }

    private String getTypeString(boolean isRollback) {
        if (isRollback) {
            return "有数据被回滚啦";
        } else {
            return "有新的数据发布啦";
        }
    }

    private EhrMessageDTO createEhrMessageDTO(String email) {
        EhrMessageDTO dto = new EhrMessageDTO();
        dto.setApi_time((System.currentTimeMillis() / 1000));
        dto.setClient_id(ehrClientId);
        dto.setEmail(email);
        dto.setApi_sign(dto.getApiSign());
        return dto;
    }

    public String getDDUserId(String email) {
        // Check if value is present in the cache
        if (cache.containsKey(email)) {
//            System.out.println("走了缓存");
            return cache.get(email);
        }

        try {
            EhrMessageDTO ehrMsg = createEhrMessageDTO(email);
            String urlString = ehrMsg.GetUrl();
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set the request method to GET
            connection.setRequestMethod("GET");

            // Get the response code
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // Read the response
            if (responseCode == HttpURLConnection.HTTP_OK) {
                ObjectMapper objectMapper = new ObjectMapper();
                EhrMessageDTO.Response responseObj = objectMapper.readValue(connection.getInputStream(), EhrMessageDTO.Response.class);

                if (responseObj.getData().getItems().isEmpty()) {
                    System.out.println("responseObj: 没有查到相关信息返回为空 \n");
                    return "";
                }

                System.out.printf("responseObj: %s \n", responseObj.getData().getItems());

                String ddUserId = responseObj.getData().getItems().get(0).getDd_user_id();
                // Put the value into the cache
                cache.put(email, ddUserId);

                return ddUserId;
            } else {
                System.out.printf("GET request 请求失败, %s, url :%s\n", connection.getResponseMessage(), urlString);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }
}
