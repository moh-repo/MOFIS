package com.dilaw.mohe.report.pdf;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

public final class PdfResponses {

    public static boolean isInlineDisposition(String disposition) {
        if (disposition == null || disposition.isBlank()) {
            return true;
        }
        return !"attachment".equalsIgnoreCase(disposition.trim());
    }

    public static ResponseEntity<byte[]> pdf(byte[] body, String filename, boolean inline) {
        ContentDisposition cd = inline
                ? ContentDisposition.inline().build()
                : ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(cd);
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private PdfResponses() {
    }
}
