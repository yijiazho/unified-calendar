package com.unifiedcalendar.email;

import java.util.List;

public record SendEmailRequest(
        String from,
        List<String> to,
        String subject,
        String html,
        List<Attachment> attachments
) {}
