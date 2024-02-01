package com.ctrip.framework.apollo.common.dto;

import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.security.MessageDigest;

@Component
public class EhrMessageDTO {
    private long api_time;
    private String client_id;
    private String email;

    private String api_sign;

    public long getApi_time() {
        return api_time;
    }

    public void setApi_time(long api_time) {
        this.api_time = api_time;
    }

    public String getClient_id() {
        return client_id;
    }

    public void setClient_id(String client_id) {
        this.client_id = client_id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getApi_sign() {
        return api_sign;
    }

    public void setApi_sign(String api_sign) {
        this.api_sign = api_sign;
    }

    static String token = "moWcMHCdOggXJ81g";

    public String getApiSign() {
        String s = "api_time" + this.api_time + "client_id" + this.client_id + "email" + this.email + token;
        return getMD5(s);
    }

    public static String getMD5(String input) {
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

    static String url = "http://it-project.mobvista.com/ehr-api/employee?";

    public String GetUrl() {
        return url + "api_time=" + String.valueOf(this.api_time) + "&client_id=" + this.client_id + "&email=" + this.email + "&api_sign=" + this.api_sign;
    }

    public static class User {
        private String nid;
        private String username;
        private String email;
        private String dd_user_id;

        public String getNid() {
            return nid;
        }
        public void setNid(String nid) {
            this.nid = nid;
        }
        public String getUsername() {
            return username;
        }
        public void setUsername(String username) {
            this.username = username;
        }
        public String getEmail() {
            return email;
        }
        public void setEmail(String email) {
            this.email = email;
        }
        public String getDd_user_id() {
            return dd_user_id;
        }
        public void setDd_user_id(String dd_user_id) {
            this.dd_user_id = dd_user_id;
        }
    }

    public static class Data {
        private List<User> items;
        private int total;
        private int page;
        private int pageSize;

        public List<User> getItems() {
            return items;
        }
        public void setItems(List<User> items) {
            this.items = items;
        }
        public int getTotal() {
            return total;
        }
        public void setTotal(int total) {
            this.total = total;
        }
        public int getPage() {
            return page;
        }
        public int getPageSize() {
            return pageSize;
        }
    }

    public static class Response {
        private static String info;
        private static Data data;
        private static int status;

        public String getInfo() {
            return info;
        }
        public void setInfo(String info) {
            Response.info = info;
        }
        public Data getData() {
            return data;
        }
        public void setData(Data data) {
            Response.data = data;
        }
        public int getStatus() {
            return status;
        }
        public void setStatus(int status) {
            Response.status = status;
        }
    }
}

