package com.email.writer.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.Objects;

@Service
public class EmailGeneratorService  {

    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;
    @Value("${gemini.api.key}")
    private String geminiApiKey;
    //String apiKey = System.getenv("GEMINI_API_KEY");

    public EmailGeneratorService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String generateEmailReply(EmailRequest emailRequest){
        //build the prompt
        String prompt = buildPrompt(emailRequest);
        System.out.println("Prompt to Gemini:\n" + prompt);

        //craft the request
        Map <String , Object> requestBody = Map.of(
                "contents",new Object[]{
                        Map.of("parts",new Object[]{
                                Map.of("text", prompt)
                        })
                }
        );

        //do the request and get response
        try {
            String response = webClient.post()
                    //   .uri(geminiApiUrl + geminiApiKey)
                    .uri(geminiApiUrl)
                    .header("Authorization", "Bearer " + geminiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            //Extract response and return
            return extractResponseContent(response);
        } catch (WebClientResponseException ex) {
            // Better error logging on API failure
            return "Gemini API Error: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString();
        } catch (Exception ex) {
            return "Unexpected error while calling Gemini API: " + ex.getMessage();
        }
    }

    private String extractResponseContent(String response) {
        try{
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response);
            JsonNode candidates =  rootNode.path("candidates");
            if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
                return "No reply generated. Gemini response:\n" + response;
            }
                   return candidates.get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

        } catch (Exception e) {
            return "Error processing request: " + e.getMessage() + "\nFull response:\n" + response;
        }

    }

    //this prompt goes as basic input to gemini ai api with original content fo email
    private String buildPrompt(EmailRequest emailRequest) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a professional AI assistant that helps users draft email. ")
        .append("Your task is to write a well-structured, polite, and contextually accurate reply to the given email content. ");

        if(emailRequest.getTone() != null && !emailRequest.getTone().isEmpty()){
            prompt.append("Use a ").append(emailRequest.getTone()).append(" tone.");
        }
        prompt.append("Format the email with:\n")
                .append("1. A suitable greeting based on the sender's tone (e.g., Hi, Hello, Dear [Name] if available),\n")
                .append("2. A professional and relevant body that addresses the sender’s query or message,\n")
                .append("3. A polite closing statement,\n")
                .append("4. A sign-off like 'Best regards' or 'Thank you'.\n")
                .append("Avoid including a subject line. Do not mention that you are an AI.\n\n");
        prompt.append("\n--- Original email--- \n")
                .append(emailRequest.getEmailContent())
                .append("\n--- End of Email ---\n\n")
                .append("Now generate a complete email reply based on the above message.");


        return prompt.toString();
    }

}
