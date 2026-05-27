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
    public ResponseEntity<Map<String, Object>> getToken(
            @RequestParam(required = false) String appointmentId,
            @RequestParam(required = false) String channelName,
            @RequestParam(defaultValue = "0") int uid
    ) {
        if (uid < 1 || uid > 65535) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Invalid Agora uid. Use an integer from 1 to 65535.");
            error.put("receivedUid", uid);
            return ResponseEntity.badRequest().body(error);
        }

        String finalChannelName;

        if (appointmentId != null && !appointmentId.isBlank()) {
            finalChannelName = "therapy-session-" + appointmentId;
        } else if (channelName != null && !channelName.isBlank()) {
            finalChannelName = channelName;
        } else {
            finalChannelName = "therapy-session-default";
        }

        int expirationTimeInSeconds = 3600;
        int timestamp = (int) (System.currentTimeMillis() / 1000) + expirationTimeInSeconds;

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();

        String token = tokenBuilder.buildTokenWithUid(
                appId,
                appCertificate,
                finalChannelName,
                uid,
                Role.ROLE_PUBLISHER,
                timestamp,
                timestamp
        );

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("channelName", finalChannelName);
        response.put("appId", appId);
        response.put("appointmentId", appointmentId);
        response.put("uid", uid);
        response.put("expiresIn", expirationTimeInSeconds);

        return ResponseEntity.ok(response);
    }
}
