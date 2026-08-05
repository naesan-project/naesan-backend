package com.naesan.share.adapter.in.web;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

public final class TrustedProxyClientIpResolver {
    static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final IpAddressMatcher IPV4_ADDRESS =
            new IpAddressMatcher("0.0.0.0/0");
    private static final IpAddressMatcher IPV6_ADDRESS =
            new IpAddressMatcher("::/0");

    private final List<IpAddressMatcher> trustedProxyMatchers;

    public TrustedProxyClientIpResolver(String trustedProxyCidrs) {
        this.trustedProxyMatchers = Arrays.stream(trustedProxyCidrs.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }
        List<String> proxyChain = Arrays.stream(forwardedFor.split(","))
                .map(String::strip)
                .toList();
        for (int index = proxyChain.size() - 1; index >= 0; index--) {
            String address = proxyChain.get(index);
            if (!isIpAddress(address)) {
                return remoteAddress;
            }
            if (!isTrustedProxy(address)) {
                return address;
            }
        }
        return remoteAddress;
    }

    private boolean isTrustedProxy(String remoteAddress) {
        return trustedProxyMatchers.stream()
                .anyMatch(matcher -> matcher.matches(remoteAddress));
    }

    private boolean isIpAddress(String address) {
        try {
            return IPV4_ADDRESS.matches(address) || IPV6_ADDRESS.matches(address);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
