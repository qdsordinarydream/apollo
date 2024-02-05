package com.ctrip.framework.apollo.portal.listener;

import com.ctrip.framework.apollo.common.dto.DingTalkMessageDTO;
import com.ctrip.framework.apollo.common.dto.EhrMessageDTO;
import com.ctrip.framework.apollo.portal.api.API;
import com.ctrip.framework.apollo.portal.entity.bo.ItemBO;
import com.ctrip.framework.apollo.portal.entity.bo.ReleaseHistoryBO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MtgListener extends API {
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    ObjectMapper objectMapper = new ObjectMapper();

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

        Map<String, String> ddUserIds = new HashMap<>();
        // 如果是回滚事件，发布是 configGroup 维度，所以只能是操作人一个人，不存在多人修改的情况
        if (publishInfo.isRollbackEvent()) {
            ddUserIds.put(getDDUserId(releaseHistory.getOperator()), "");
        } else {
            if (!publishInfo.getChangeItems().isEmpty()) {
                for (ItemBO entry : publishInfo.getChangeItems()) {
                    System.out.printf("收到的变更 key: %s, old: %s, value: %s \n", entry.getItem().getKey(), entry.getOldValue(), entry.getNewValue());
                    ddUserIds.put(getDDUserId(entry.getItem().getDataChangeLastModifiedBy()), "");
                }
            }
        }

        System.out.printf("ddUserId: %s \n", ddUserIds);

        // Create JSON payload
        DingTalkMessageDTO dto = createDingTalkMessageDTO(releaseHistory, publishInfo, ddUserIds);
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return;
        }

        // Set up the URL and open the connection
        try {
            URL url = new URL(ddUrl + ddToken);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set the request method to POST
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Write JSON payload to the connection's output stream
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Get the response code
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("send to dingding success");
    }


    private DingTalkMessageDTO createDingTalkMessageDTO(ReleaseHistoryBO releaseHistory, ConfigPublishEvent.ConfigPublishInfo publishInfo, Map<String, String> dd) {
        DingTalkMessageDTO dto = new DingTalkMessageDTO();
        List<String> opreators = new ArrayList<>(dd.keySet());
        if (publishInfo.getChangeItems() == null) {
            publishInfo.setChangeItems(new ArrayList<>());
        }

        dto.setMsgtype("markdown");
        DingTalkMessageDTO.Markdown md = new DingTalkMessageDTO.Markdown();
        String tps = getTypeString(publishInfo.isRollbackEvent());
        md.setTitle(tps);
        md.setText(
                "#### **" + tps + "** \n " +
                        "#### ***操作人***: " + getOperator(opreators) + " \n " +
                        "#### ***发布人***: " + releaseHistory.getOperator() + " \n " +
                        "#### ***Appid***: " + releaseHistory.getAppId() + " \n " +
                        "#### ***Cluster***: " + releaseHistory.getClusterName() + " \n " +
                        "#### ***ConfigGroup***: " + releaseHistory.getNamespaceName() + " \n " +
                        "#### ***变更内容***: " + publishInfo.getChangeItems().toString() + " \n " +
                        "> ![screenshot](" + pictureUrl + ") \n " +
                        "> ###### 详情信息 => [地址](" + getUrl(releaseHistory.getAppId(), releaseHistory.getClusterName()) + ") \n");
        dto.setMarkdown(md);
        DingTalkMessageDTO.At at = new DingTalkMessageDTO.At();
        at.setAtUserIds(opreators);
        dto.setAt(at);
        return dto;
    }

    private String getOperator(List<String> dds) {
        StringBuilder resp = new StringBuilder();
        for (String dd : dds) {
            resp.append("@").append(dd).append(",");
        }
        return resp.toString();
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
            System.out.println("走了缓存");
            return cache.get(email);
        }

        EhrMessageDTO ehrMsg = createEhrMessageDTO(email);
        String urlString = ehrMsg.GetUrl();
        String response = "";
        try {
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
                System.out.println("GET request not worked");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }
}
