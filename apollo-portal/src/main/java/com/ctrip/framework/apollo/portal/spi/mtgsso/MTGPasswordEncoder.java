package com.ctrip.framework.apollo.portal.spi.mtgsso;

import com.ctrip.framework.apollo.portal.spi.springsecurity.ApolloPasswordEncoderFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

public class MTGPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder encoder;

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
        //System.out.println("MTGPasswordEncoder matches: " + rawPassword + " " + encodedPassword);
        return true;
    }

}
