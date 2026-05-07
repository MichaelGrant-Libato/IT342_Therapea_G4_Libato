package com.therapea.backend.features.videocall;

import io.agora.media.RtcTokenBuilder2;
import io.agora.media.RtcTokenBuilder2.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/video")
@CrossOrigin(origins = "*")
public class VideoCallController {

    @Value("${agora.app-id}")
    private String appId;

    @Value("${agora.app-certificate}")
    private String appCertificate;

    @GetMapping("/token")
    public ResponseEntity<Map<String, String>> getToken(@RequestParam String channelName) {
        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

        int expirationTimeInSeconds = 3600;
        int timestamp = (int)(System.currentTimeMillis() / 1000 + expirationTimeInSeconds);

        String result = tokenBuilder.buildTokenWithUid(
                appId,
                appCertificate,
                channelName,
                0,
                Role.ROLE_PUBLISHER,
                timestamp,
                timestamp
        );

        Map<String, String> response = new HashMap<>();
        response.put("token", result);
        response.put("channelName", channelName);
        response.put("appId", appId);

        return ResponseEntity.ok(response);
    }
}