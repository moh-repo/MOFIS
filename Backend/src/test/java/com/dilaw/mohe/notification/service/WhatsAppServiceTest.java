package com.dilaw.mohe.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The WhatsApp request body is an administrator's template with the message put
 * into it. The message is the user's free text, so it must land as one JSON string
 * whatever it contains — quotes, newlines, a stray placeholder.
 */
class WhatsAppServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("the default template is the Cloud API text message, to the number's digits")
    void defaultTemplate() throws Exception {
        JsonNode json = MAPPER.readTree(WhatsAppService.render(null, "+962 79-000-0000", "x", "Hello"));
        assertThat(json.get("messaging_product").asText()).isEqualTo("whatsapp");
        assertThat(json.get("to").asText()).isEqualTo("962790000000");
        assertThat(json.at("/text/body").asText()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("quotes, newlines and placeholders in the message stay inside the body field")
    void messageCannotBreakOut() throws Exception {
        String body = "He said \"hi\"\nthen {{to}} \\ end";
        JsonNode json = MAPPER.readTree(WhatsAppService.render(
                "{\"phone\":\"{{to}}\",\"sender\":\"{{from}}\",\"message\":\"{{body}}\"}", "+1555", "MOHE", body));
        assertThat(json.get("message").asText()).isEqualTo(body);
        assertThat(json.get("phone").asText()).isEqualTo("+1555");
        assertThat(json.get("sender").asText()).isEqualTo("MOHE");
        assertThat(json.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("a template is refused unless it names the recipient and the message and fills in to JSON")
    void templateProblems() {
        assertThat(WhatsAppService.templateProblem(null)).isNull();
        assertThat(WhatsAppService.templateProblem("{\"to\":\"{{to}}\",\"text\":\"{{body}}\"}")).isNull();
        assertThat(WhatsAppService.templateProblem("{\"text\":\"{{body}}\"}")).contains("{{to}}");
        assertThat(WhatsAppService.templateProblem("{\"to\":\"{{to}}\"}")).contains("{{body}}");
        assertThat(WhatsAppService.templateProblem("{\"to\":{{to}},\"text\":\"{{body}}\"}")).contains("not valid JSON");
    }

    @Test
    @DisplayName("{{bodyOneLine}} has no new lines, tabs or runs of spaces, which Meta refuses in a template parameter")
    void oneLine() throws Exception {
        JsonNode json = MAPPER.readTree(WhatsAppService.render(WhatsAppService.EXAMPLE_APPROVED_TEMPLATE,
                "+962790000000", null, "Dear student,\n\n\tFees    are due.\r\nThank you"));
        assertThat(json.at("/template/components/0/parameters/0/text").asText())
                .isEqualTo("Dear student, | Fees are due. | Thank you");
        assertThat(WhatsAppService.templateProblem(WhatsAppService.EXAMPLE_APPROVED_TEMPLATE)).isNull();
    }

    @Test
    @DisplayName("the e-mail editor's HTML becomes readable text for SMS and WhatsApp")
    void plainText() {
        assertThat(MessageText.plain("<p>Dear student,</p><p>Fees are due &amp; payable.</p>"))
                .isEqualTo("Dear student,\nFees are due & payable.");
        assertThat(MessageText.plain("<ul><li>one</li><li>two</li></ul>")).isEqualTo("- one\n- two");
        assertThat(MessageText.plain("line one<br>line two")).isEqualTo("line one\nline two");
        assertThat(MessageText.plain("no markup at all")).isEqualTo("no markup at all");
    }
}
