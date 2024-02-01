package com.ctrip.framework.apollo.portal.api;

import com.ctrip.framework.apollo.common.dto.DingTalkMessageDTO;
import com.ctrip.framework.apollo.common.dto.EhrMessageDTO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DingTalkMessage extends API {
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    ObjectMapper objectMapper = new ObjectMapper();

    public static String url = "https://oapi.dingtalk.com/robot/send";

//    @Value("${dingding.token}")
//    private String token;
    public static String token = "18f9cc9e94b8790fddbfacff6805cd7ec97733e994652b791dadcab16878fb12";

    // 发送 POST 请求到钉钉机器人
    public void sendDingTalkMessage(String key, String val, String appid, String clusterName, String cfGroup, String operator, int tp) {
        System.out.println("send to dingding");

        String ddUserId = getDDUserId(operator);
        System.out.printf("ddUserId: %s \n", ddUserId);

        // Create JSON payload
        DingTalkMessageDTO dto = createDingTalkMessageDTO(key, val, appid, clusterName, cfGroup, ddUserId, tp);
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return;
        }

        // Set up the URL and open the connection
        try {
            URL url = new URL(DingTalkMessage.url + "?access_token=" + token);
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

    public String host = "localhost:8070";
    //static String userId = "16847239033728710";
    static String pictureUrl = "https://picx.zhimg.com/70/v2-83050f35478311acec9c1c31dd90e908_1440w.avis?source=172ae18b&biz_tag=Post";

    private DingTalkMessageDTO createDingTalkMessageDTO(String key, String val, String appid, String clusterName, String cfGroup, String dd, int tp) {
        DingTalkMessageDTO dto = new DingTalkMessageDTO();
        dto.setMsgtype("markdown");
        DingTalkMessageDTO.Markdown md = new DingTalkMessageDTO.Markdown();
        String tps = getTypeString(tp);
        md.setTitle(tps);
        md.setText(
                "#### **" + tps + "** \n " +
                        "#### **操作人**: @" + dd + " \n " +
                        "#### **appid**: " + appid + " \n " +
                        "#### **cluster**: " + clusterName + " \n " +
                        "#### **configGroup**: " + cfGroup + " \n " +
                        "#### **key**: " + key + " \n " +
                        "#### **val**: " + val + " \n " +
                        "> ![screenshot](" + pictureUrl + ") \n " +
                        "> ###### 信息审核 => [地址](" + getUrl(appid, clusterName) + ") \n");
        dto.setMarkdown(md);
        DingTalkMessageDTO.At at = new DingTalkMessageDTO.At();
        at.setAtUserIds(Collections.singletonList(dd));
        dto.setAt(at);
        return dto;
    }

    private String getUrl(String appid, String cluster) {
        if (!Objects.equals(cluster, "default")) {
            return "http://" + host + "/config.html?#/appid=" + appid + "&cluster=" + cluster;
        }
        return "http://" + host + "/config.html?#/appid=" + appid;
    }

    private String getTypeString(int type) {
        switch (type) {
            case 1:
                return "有数据新增啦";
            case 2:
                return "有数据修改啦";
            case 3:
                return "有数据删除啦";
            default:
                return "";
        }
    }

    static String ClientId = "dqU8p4FUVRx6Kr9s";
    private EhrMessageDTO createEhrMessageDTO(String email) {
        EhrMessageDTO dto = new EhrMessageDTO();
        dto.setApi_time((System.currentTimeMillis() / 1000));
        dto.setClient_id(ClientId);
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

                System.out.printf("responseObj: %s \n", responseObj.getData().getItems());

                String ddUserId = responseObj.getData().getItems().get(0).getDd_user_id();
                // Put the value into the cache
                cache.put(email, ddUserId);

                return ddUserId;
            }else {
                System.out.println("GET request not worked");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }
}
