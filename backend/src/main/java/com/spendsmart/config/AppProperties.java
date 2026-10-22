package com.spendsmart.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Security security, Web web, Docs docs) {

    public AppProperties {
        cors = cors == null ? new Cors(List.of()) : cors;
        security = security == null ? new Security(5, Duration.ofMinutes(15), 10) : security;
        web = web == null ? new Web("") : web;
        docs = docs == null ? new Docs(true) : docs;
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of()
                    : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public record Security(int loginMaxFailures, Duration loginLockout, int registrationPerIpPerHour) {
    }

    public record Web(String staticDir) {
    }

    public record Docs(boolean enabled) {
    }
}
