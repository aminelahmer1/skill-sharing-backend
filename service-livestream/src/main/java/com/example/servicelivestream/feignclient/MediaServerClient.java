package com.example.servicelivestream.feignclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "media-server", url = "${media.server.url:http://localhost:5080}")
public interface MediaServerClient {
    @PostMapping("/rest/v2/broadcasts/{streamId}/token")
    String generateToken(
            @PathVariable("streamId") String streamId,
            @RequestParam("type") String type,
            @RequestParam("expireDate") long expireDate
    );
}