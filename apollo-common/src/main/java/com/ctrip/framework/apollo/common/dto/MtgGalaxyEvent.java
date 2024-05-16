package com.ctrip.framework.apollo.common.dto;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MtgGalaxyEvent {
    public MtgGalaxyEvent(String appid, String cluster, String namespace, String detail, String email, long now) {
        Event event = new Event(appid, cluster, namespace, detail, email, now);
        this.events = new ArrayList<>();
        this.events.add(event);
    }

    private final int namespaceId = 1;
    private final String platform = "cybercore";

    private List<Event> events; // 修改了变量名，使其更具可读性

    public static class Event {
        public Event(String appid, String cluster, String namespace, String detail, String email, long now) {
            this.changeTarget = appid + "_" + cluster + "_" + namespace;
            this.changeDetail = detail;
            this.changeStartTime = now;
            this.changeEndTime = now;
            this.changeUserEmail = email;
            this.tags = new HashMap<>();
            this.tags.put("appid", appid);
            this.tags.put("cluster", cluster);
            this.tags.put("namespace", namespace);
        }

        private String changeTarget;
        private String changeDetail;
        private long changeStartTime;
        private long changeEndTime;
        private String changeUserEmail;
        private Map<String, String> tags;

        public JSONObject toJSONObject() {
            JSONObject jsonEvent = new JSONObject();
            jsonEvent.put("changeTarget", this.changeTarget);
            jsonEvent.put("changeDetail", this.changeDetail);
            jsonEvent.put("changeStartTime", this.changeStartTime);
            jsonEvent.put("changeEndTime", this.changeEndTime);
            jsonEvent.put("changeUserEmail", this.changeUserEmail);
            jsonEvent.put("tags", this.tags); // 将Map转换为JSONObject
            return jsonEvent;
        }
    }

    public JSONObject toJson() {
        JSONObject jsonEvent = new JSONObject();
        JSONArray eventsArray = new JSONArray(); // 创建一个JSONArray来存储所有事件

        jsonEvent.put("namespaceId", this.namespaceId);
        jsonEvent.put("platform", this.platform);

        // 遍历events列表，将每个事件转换为JSON对象并添加到eventsArray中
        for (Event event : events) {
            eventsArray.put(event.toJSONObject());
        }

        jsonEvent.put("events", eventsArray); // 将eventsArray添加到jsonEvent中
        return jsonEvent;
    }
}