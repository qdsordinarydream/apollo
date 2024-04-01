package com.ctrip.framework.apollo.portal.listener;

import com.ctrip.framework.apollo.common.dto.EhrMessageDTO;
import com.ctrip.framework.apollo.portal.api.API;
import com.ctrip.framework.apollo.portal.component.RetryableRestTemplate;
import com.ctrip.framework.apollo.portal.entity.bo.ItemBO;
import com.ctrip.framework.apollo.portal.entity.bo.ReleaseHistoryBO;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiRobotSendRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MtgListener extends API {
    private Logger logger = LoggerFactory.getLogger(RetryableRestTemplate.class);
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
        if (publishInfo.getChangeItems().isEmpty()) {
            logger.warn("发布信息为空，不发送钉钉通知");
            return;
        }
        Map<String, String> ddUserIds = new HashMap<>();

        // 如果是回滚事件，发布是 configGroup 维度，所以只能是操作人一个人，不存在多人修改的情况
        if (publishInfo.isRollbackEvent()) {
            ddUserIds.put(getDDUserId(releaseHistory.getOperator()), "");
        } else {
            for (ItemBO entry : publishInfo.getChangeItems()) {
                if (entry.getItem().getDataChangeLastModifiedBy() != null) {
                    ddUserIds.put(getDDUserId(entry.getItem().getDataChangeLastModifiedBy()), "");
                }
            }
        }

        try {
            sendMsg(releaseHistory, publishInfo, ddUserIds);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        client.execute(req);
    }

    private String createMarkdownContent(ReleaseHistoryBO releaseHistory, ConfigPublishEvent.ConfigPublishInfo publishInfo, List<String> operators) {
        return "#### **" + getTypeString(publishInfo.isRollbackEvent()) + "** \n " +
                "#### ***操作人***: [" + getOperator(operators) + "] \n " +
                "#### ***发布人***: [" + releaseHistory.getOperator() + "] \n " +
                "#### ***Appid***: [" + releaseHistory.getAppId() + "] \n " +
                "#### ***Cluster***: [" + releaseHistory.getClusterName() + "] \n " +
                "#### ***ConfigGroup***: [" + releaseHistory.getNamespaceName() + "] \n " +
                "#### ***变更内容***: \n " +
                " <pre><code> " + spliceItems(publishInfo.getChangeItems()) + " </code></pre> \n " +
                "> ![screenshot](" + pictureUrl + ") \n " +
                "> ###### 详情信息 => [地址](" + getUrl(releaseHistory.getAppId(), releaseHistory.getClusterName()) + ") \n";
    }

    private String spliceItems(List<ItemBO> list) {
        StringBuilder sb = new StringBuilder();
        for (ItemBO item : list) {
            sb.append(" ##### ***Key***: " + item.getItem().getKey() + " \n");
            sb.append(" ##### ***OldValue***:\n " + item.getOldValue() + " \n");
            sb.append(" ##### ***NewValue***:\n " + item.getNewValue() + " \n");
        }
        return sb.toString();
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

            // Read the response
            if (responseCode == HttpURLConnection.HTTP_OK) {
                ObjectMapper objectMapper = new ObjectMapper();
                EhrMessageDTO.Response responseObj = objectMapper.readValue(connection.getInputStream(), EhrMessageDTO.Response.class);

                if (responseObj.getData().getItems().isEmpty()) {
                    logger.error("responseObj: 没有查到相关信息返回为空");
                    return "";
                }

                String ddUserId = responseObj.getData().getItems().get(0).getDd_user_id();
                // Put the value into the cache
                cache.put(email, ddUserId);

                return ddUserId;
            } else {
                logger.error("GET请求未成功, url: " + urlString + ", responseCode: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }
}
