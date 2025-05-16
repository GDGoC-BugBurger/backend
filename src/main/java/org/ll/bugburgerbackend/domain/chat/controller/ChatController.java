package org.ll.bugburgerbackend.domain.chat.controller;

import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.ll.bugburgerbackend.global.webMvc.LoginUser;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ll.bugburgerbackend.domain.chat.service.ChatService;
import org.ll.bugburgerbackend.global.type.ChatType;
import lombok.RequiredArgsConstructor;

import java.util.Base64;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.util.Map;

// TODO: Service로 서비스 로직 분리할 것
@Slf4j
@Controller
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatController {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.prompt}")
    private String geminiPrompt;

    private final ChatService chatService;

    @GetMapping("/")
    public String chat() {
        return "chat";
    }

    @PostMapping(value = "/speech-to-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> speechToText(@RequestParam("audio") MultipartFile audioFile, @LoginUser Member loginMember) throws IOException {
        if(loginMember == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        geminiPrompt = String.format(
                loginMember.getUsername(),
                loginMember.getBirth(),
                loginMember.getGender(),
                loginMember.getDementiaStage(),
                loginMember.getInterests(),
                loginMember.getBackground(),
                loginMember.getFamily(),
                loginMember.getRecentAnalysis()
        );

        log.info("Received audio file for speech-to-text: size={} bytes", audioFile.getSize());
        byte[] audioBytes = audioFile.getBytes();
        String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);

        // 1. 환자의 음성 메시지를 텍스트로 변환 (예: STT API 호출)
        String patientText = speechToTextByExternalApi(audioBytes); // 실제 STT API 연동 필요
        log.info("Patient speech-to-text result: {}", patientText);

        // 2. Gemini 프롬프트 준비
        ObjectMapper mapper = new ObjectMapper();
        String escapedPrompt = mapper.writeValueAsString(geminiPrompt);

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
                  "text": %s
                }
              ]
            }
          ]
        }
        """.formatted(audioBase64, escapedPrompt);

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

        // 3. 환자의 음성 메시지(텍스트 변환 결과) 저장
        chatService.saveChat(loginMember, patientText, ChatType.PATIENT);

        // 4. AI의 응답 메시지 저장
        chatService.saveChat(loginMember, transcript, ChatType.AI);

        return ResponseEntity.ok().body(Map.of("text", patientText, "ai", transcript));
    }

    // 실제 음성 → 텍스트 변환 로직 (외부 STT API 연동 필요)
    private String speechToTextByExternalApi(byte[] audioBytes) {
        // 예시: 외부 STT API 연동 구현
        // 실제 구현에서는 Google STT, Naver CLOVA Speech 등 사용
        // 여기서는 예시로 "환자 음성 텍스트" 반환
        return "환자 음성 텍스트";
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
