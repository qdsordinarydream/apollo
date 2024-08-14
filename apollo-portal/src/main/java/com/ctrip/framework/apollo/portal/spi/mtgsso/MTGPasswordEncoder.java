package com.ctrip.framework.apollo.portal.spi.mtgsso;

import com.ctrip.framework.apollo.portal.spi.springsecurity.ApolloPasswordEncoderFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MTGPasswordEncoder implements PasswordEncoder {
    private final PasswordEncoder encoder;

    private static final String PREFIX = "{";

    private static final String SUFFIX = "}";

    public MTGPasswordEncoder() {
        this.encoder = ApolloPasswordEncoderFactory.createDelegatingPasswordEncoder();
    }
    @Override
    public String encode(CharSequence rawPassword) {
        //System.out.println("MTGPasswordEncoder encode: " + rawPassword);
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        // if password is apollo, it means it is sso mode
        if (rawPassword.toString().equals("apollo")) {
            return true;
        }

        boolean matches = this.encoder.matches(rawPassword, encodedPassword);
        if (matches) {
            return true;
        }
        String id = this.extractId(encodedPassword);
        if (StringUtils.hasText(id)) {
            throw new IllegalArgumentException(
                    "There is no PasswordEncoder mapped for the id \"" + id + "\"");
        }
        return false;
    }

    private String extractId(String prefixEncodedPassword) {
        if (prefixEncodedPassword == null) {
            return null;
        }
        int start = prefixEncodedPassword.indexOf(PREFIX);
        if (start != 0) {
            return null;
        }
        int end = prefixEncodedPassword.indexOf(SUFFIX, start);
        if (end < 0) {
            return null;
        }
        return prefixEncodedPassword.substring(start + 1, end);
    }

}
