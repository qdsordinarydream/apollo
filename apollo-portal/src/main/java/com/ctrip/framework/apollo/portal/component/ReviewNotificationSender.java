package com.ctrip.framework.apollo.portal.component;

import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponse;
import com.aliyun.tea.TeaException;
import com.ctrip.framework.apollo.common.dto.EhrMessageDTO;

import com.ctrip.framework.apollo.portal.api.API;
import com.ctrip.framework.apollo.portal.entity.bo.ItemBO;
import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiMessageCorpconversationAsyncsendV2Request;
import com.dingtalk.api.response.OapiMessageCorpconversationAsyncsendV2Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReviewNotificationSender extends API {
    private Logger logger = LoggerFactory.getLogger(RetryableRestTemplate.class);
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @Value("${reviewHost}")
    private String reviewHost;

    @Value("${ddPicture}")
    private String pictureUrl;

    @Value("${ehrClientId}")
    private String ehrClientId;

    @Value("${ddAppKey}")
    private String ddAppKey;

    @Value("${ddAppSecret}")
    private String ddAppSecret;

    @Value("${ddAgentId}")
    private Long ddAgentId;


    // 发送 POST 请求到钉钉机器人
    public void sendReviewNotification(String appid, String clusterName, String cfGroup, List<ItemBO> items, Set<UserInfo> releaseNamespaceUsers) {
        // 发送钉钉通知
        Map<String, String> ddUserIDs = new HashMap<>();
        if (!releaseNamespaceUsers.isEmpty()) {
            for (UserInfo user : releaseNamespaceUsers) {
                ddUserIDs.put(getDDUserId(user.getEmail()), "");
            }
        }

        // 发送具体消息
        try {
            sendMsg(appid, clusterName, cfGroup, ddUserIDs, items);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // 发送工作通知
    // https://open.dingtalk.com/document/orgapp/asynchronous-sending-of-enterprise-session-messages
    private void sendMsg(String appid, String clusterName, String cfGroup, Map<String, String> ddUserIDs, List<ItemBO> items) throws Exception {
        DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2");
        OapiMessageCorpconversationAsyncsendV2Request request = new OapiMessageCorpconversationAsyncsendV2Request();

        request.setAgentId(ddAgentId);
        StringJoiner joiner = new StringJoiner(",");
        for (String value : ddUserIDs.keySet()) {
            joiner.add(value);
        }
        // 设置接收者，多个用,分开
        request.setUseridList(joiner.toString());

        OapiMessageCorpconversationAsyncsendV2Request.Msg msg = createMessageContent(appid, clusterName, cfGroup, items);
        request.setMsg(msg);

        // 获取token
        String accessToken;
        if (cache.containsKey(ddAppKey + ddAppSecret)) {
            accessToken = cache.get(ddAppKey + ddAppSecret);
        } else {
            accessToken = getToken(ddAppKey, ddAppSecret);
            cache.put(ddAppKey + ddAppSecret, accessToken);
        }

        // 发起请求
        OapiMessageCorpconversationAsyncsendV2Response rsp = client.execute(request, accessToken);
        // token 过期，重新获取
        if (Objects.equals(rsp.getSubCode(), "40014")) {
            accessToken = getToken(ddAppKey, ddAppSecret);
            cache.put(ddAppKey + ddAppSecret, accessToken);
            client.execute(request, accessToken);
        }
    }

    private OapiMessageCorpconversationAsyncsendV2Request.Msg createMessageContent(String appid, String clusterName, String cfGroup, List<ItemBO> items) {
        OapiMessageCorpconversationAsyncsendV2Request.Msg msg = new OapiMessageCorpconversationAsyncsendV2Request.Msg();
        // 拼接历史操作人
        Map<String, String> ddUserIds = new HashMap<>();
        if (!items.isEmpty()) {
            for (ItemBO entry : items) {
                ddUserIds.put(entry.getItem().getDataChangeLastModifiedBy(), "");
            }
        }
        StringJoiner joiner = new StringJoiner(",");
        for (String value : ddUserIds.keySet()) {
            joiner.add(value);
        }

        msg.setMsgtype("markdown");
        msg.setMarkdown(new OapiMessageCorpconversationAsyncsendV2Request.Markdown());
        msg.getMarkdown().setTitle("配置中心有“数据修改”待您审核发布");
        msg.getMarkdown().setText(
                "#### **" + "配置中心有“数据修改”待您审核发布" + "** \n " +
                        "#### ***修改人***: [" + joiner.toString() + "] \n " +
                        "#### ***Appid***: [" + appid + "] \n " +
                        "#### ***Cluster***: [" + clusterName + "] \n " +
                        "#### ***ConfigGroup***: [" + cfGroup + "] \n " +
                        "#### ***修改详情***: " + spliceItems(items) + " \n " +
                        "> ![screenshot](" + pictureUrl + ") \n " +
                        "> ###### 信息审核 => [地址](" + getUrl(appid, clusterName) + ") \n");

        return msg;
    }

    private String spliceItems(List<ItemBO> list) {
        StringBuilder sb = new StringBuilder();
        for (ItemBO item : list) {
            sb.append(" ##### ***Key***: " + item.getItem().getKey() + " \n ");
            sb.append(" ##### ***OldValue***: \n " + item.getOldValue() + " \n  \n ");
            sb.append(" ##### ***NewValue***: \n " + item.getNewValue() + " \n  \n ");
        }
        return sb.toString();
    }

    private String getUrl(String appid, String cluster) {
        if (!Objects.equals(cluster, "default")) {
            return "http://" + reviewHost + "/config.html?#/appid=" + appid + "&cluster=" + cluster;
        }
        return "http://" + reviewHost + "/config.html?#/appid=" + appid;
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
        if (cache.containsKey(email)) {
            return cache.get(email);
        }

        EhrMessageDTO ehrMsg = createEhrMessageDTO(email);
        String urlString = ehrMsg.GetUrl();
        String response = "";
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                ObjectMapper objectMapper = new ObjectMapper();
                EhrMessageDTO.Response responseObj = objectMapper.readValue(connection.getInputStream(), EhrMessageDTO.Response.class);

                if (responseObj.getData().getItems().isEmpty()) {
                    logger.error("responseObj: 没有查到相关信息返回为空");
                    return "";
                }

                String ddUserId = responseObj.getData().getItems().get(0).getDd_user_id();
                cache.put(email, ddUserId);

                return ddUserId;
            } else {
                logger.error("GET请求未成功");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }


    /**
     * 使用 Token 初始化账号Client
     *
     * @return Client
     * @throws Exception
     */
    private com.aliyun.dingtalkoauth2_1_0.Client createAuthClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config();
        config.protocol = "https";
        config.regionId = "central";
        return new com.aliyun.dingtalkoauth2_1_0.Client(config);
    }

    private String getToken(String appKey, String appSecret) throws Exception {
        com.aliyun.dingtalkoauth2_1_0.Client client = this.createAuthClient();
        com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest getAccessTokenRequest = new com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest()
                .setAppKey(appKey)
                .setAppSecret(appSecret);
        try {
            GetAccessTokenResponse resp = client.getAccessToken(getAccessTokenRequest);
            return resp.getBody().getAccessToken();
        } catch (TeaException err) {
            if (!com.aliyun.teautil.Common.empty(err.code) && !com.aliyun.teautil.Common.empty(err.message)) {
                logger.error("GET request 请求失败, err: " + err.message + ",  err code:" + err.code);
            }

        } catch (Exception _err) {
            TeaException err = new TeaException(_err.getMessage(), _err);
            if (!com.aliyun.teautil.Common.empty(err.code) && !com.aliyun.teautil.Common.empty(err.message)) {
                // err 中含有 code 和 message 属性，可帮助开发定位问题
                logger.error("GET request 请求失败, err: " + err.message + ",  err code:" + err.code);
            }
        }
        return "";
    }
}
