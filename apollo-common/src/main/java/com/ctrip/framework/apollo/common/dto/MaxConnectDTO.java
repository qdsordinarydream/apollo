package com.ctrip.framework.apollo.common.dto;

import java.util.Map;

public class MaxConnectDTO {
    public class MaxConnectTokenDTO {
        private String access_token;

        public void setAccess_token(String access_token) {
            this.access_token = access_token;
        }
        public String getAccess_token() {
            return access_token;
        }
    }

    public class UserInfo {
        private ThirdPartyInfo third_party_info;

        public void setThird_party_info(ThirdPartyInfo third_party_info) {
            this.third_party_info = third_party_info;
        }
        public ThirdPartyInfo getThird_party_info() {
            return third_party_info;
        }
    }


    public class ThirdPartyInfo {
        private Profiles profiles;

        public void setProfiles(Profiles profiles) {
            this.profiles = profiles;
        }
        public Profiles getProfiles() {
            return profiles;
        }
    }

    public class Profiles {
        private String email;

        public void setEmail(String email) {
            this.email = email;
        }
        public String getEmail() {
            return email;
        }
    }
}
