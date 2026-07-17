package com.unifiedcalendar.email;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendClientTest {

    @Test
    void postsEmailAsAuthorizedJson() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendClient client = new ResendClient(builder, "secret-key");
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "from": "sender@example.com",
                          "to": ["recipient@example.com"],
                          "subject": "Subject",
                          "html": "<p>Body</p>",
                          "attachments": null
                        }
                        """))
                .andRespond(withSuccess());

        client.send(new SendEmailRequest(
                "sender@example.com", List.of("recipient@example.com"),
                "Subject", "<p>Body</p>", null));

        server.verify();
    }
}
