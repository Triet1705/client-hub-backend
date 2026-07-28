package com.clienthub.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientIpResolver {

    private final List<CidrRange> trustedProxies;

    public ClientIpResolver(@Value("${app.proxy.trusted-addresses:}") String trustedAddresses) {
        this.trustedProxies =
                Arrays.stream(trustedAddresses.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .map(CidrRange::parse)
                        .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalizeLiteral(request.getRemoteAddr());
        if (remoteAddress == null) {
            return "UNKNOWN";
        }
        if (!isTrusted(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(forwardedFor)) {
            return remoteAddress;
        }

        List<String> chain = new ArrayList<>();
        for (String candidate : forwardedFor.split(",")) {
            String normalized = normalizeLiteral(candidate);
            if (normalized == null) {
                return remoteAddress;
            }
            chain.add(normalized);
        }
        chain.add(remoteAddress);

        for (int index = chain.size() - 1; index >= 0; index--) {
            String address = chain.get(index);
            if (!isTrusted(address)) {
                return address;
            }
        }
        return chain.getFirst();
    }

    private boolean isTrusted(String address) {
        return trustedProxies.stream().anyMatch(range -> range.contains(address));
    }

    private static String normalizeLiteral(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String candidate = value.trim();
        if (!candidate.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private record CidrRange(byte[] network, int prefixLength) {

        private static CidrRange parse(String value) {
            String[] parts = value.split("/", 2);
            String normalized = normalizeLiteral(parts[0]);
            if (normalized == null) {
                throw new IllegalArgumentException("Invalid trusted proxy address");
            }
            try {
                byte[] bytes = InetAddress.getByName(normalized).getAddress();
                int maxBits = bytes.length * Byte.SIZE;
                int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : maxBits;
                if (prefix < 0 || prefix > maxBits) {
                    throw new IllegalArgumentException("Invalid trusted proxy prefix");
                }
                return new CidrRange(bytes, prefix);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid trusted proxy address", exception);
            }
        }

        private boolean contains(String address) {
            try {
                byte[] candidate = InetAddress.getByName(address).getAddress();
                if (candidate.length != network.length) {
                    return false;
                }
                int fullBytes = prefixLength / Byte.SIZE;
                int remainingBits = prefixLength % Byte.SIZE;
                for (int index = 0; index < fullBytes; index++) {
                    if (candidate[index] != network[index]) {
                        return false;
                    }
                }
                if (remainingBits == 0) {
                    return true;
                }
                int mask = 0xFF << (Byte.SIZE - remainingBits);
                return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
            } catch (UnknownHostException exception) {
                return false;
            }
        }
    }
}
