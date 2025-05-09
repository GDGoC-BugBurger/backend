package org.ll.bugburgerbackend.domain.webrtc;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/api")
public class WebRTCController {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @PostMapping(value = "/speech-to-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> speechToText(@RequestParam("audio") MultipartFile audioFile) throws IOException {
        log.info("Received audio file for speech-to-text: size={} bytes", audioFile.getSize());
        byte[] audioBytes = audioFile.getBytes();
        String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);

        String jsonPayload = """
        {
          "contents": [
            {
              "role": "user",
              "parts": [
                {
                  "inline_data": {
                    "mime_type": "audio/webm",
                    "data": "%s"
                  }
                },
                {
                  "text": "음성 파일을 한국어로만 인식해서 텍스트로 변환해줘. 다른 언어는 무시하고 반드시 한국어로만 변환해."
                }
              ]
            }
          ]
        }
        """.formatted(audioBase64);

        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + geminiApiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonPayload.getBytes());
        }

        int responseCode = conn.getResponseCode();
        log.info("Gemini API response code: {}", responseCode);
        if (responseCode != 200) {
            String errorMsg = new String(conn.getErrorStream().readAllBytes());
            log.error("Gemini API error: {}", errorMsg);
            return ResponseEntity.status(responseCode).body(Map.of("error", errorMsg));
        }

        String response = new String(conn.getInputStream().readAllBytes());
        log.debug("Gemini API raw response: {}", response);
        String transcript = extractGeminiTranscript(response);
        log.info("Extracted transcript: {}", transcript);

        return ResponseEntity.ok().body(Map.of("text", transcript));
    }

    // Gemini 응답에서 텍스트 추출 (JSON 파싱 사용)
    private String extractGeminiTranscript(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            // Gemini 응답 구조에 따라 경로를 조정해야 할 수 있음
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    JsonNode textNode = parts.get(0).path("text");
                    if (!textNode.isMissingNode()) {
                        return textNode.asText();
                    }
                }
            }
            log.warn("No transcript found in Gemini response (JSON parsed)");
            return "";
        } catch (Exception e) {
            log.error("Failed to parse Gemini response JSON", e);
            return "";
        }
    }
}
