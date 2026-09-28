package com.dilaw.mohe;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * ENV-01: student documents, imports and exports carry Arabic file names. The JVM
 * encodes file names with {@code sun.jnu.encoding}, which it takes from the OS locale
 * at startup; under a POSIX/C locale every such name fails with
 * {@code InvalidPathException}. The property cannot be changed once the JVM is up, so
 * this only says so loudly at startup — the fix is {@code LANG=C.UTF-8} (or another
 * UTF-8 locale) in the service's environment.
 *
 * <p>Windows is exempt: its file APIs take UTF-16 names whatever the code page, so
 * {@code sun.jnu.encoding} (Cp1252, Cp1256, …) does not limit which names work there.
 */
@Component
public class FileNameEncodingCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FileNameEncodingCheck.class);

    @Override
    public void run(ApplicationArguments args) {
        String encoding = System.getProperty("sun.jnu.encoding");
        if (!namesFollowLocale(System.getProperty("os.name"))) {
            return;
        }
        if (!isUnicode(encoding)) {
            log.error("File names are encoded as '{}', not UTF-8: uploads, imports and exports with "
                    + "Arabic names will fail. Start the service with LANG=C.UTF-8 (or another UTF-8 locale).",
                    encoding);
        }
    }

    /** Whether file names are encoded with the OS locale: every platform but Windows. */
    static boolean namesFollowLocale(String osName) {
        return osName == null || !osName.toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    }

    static boolean isUnicode(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return false;
        }
        try {
            return Charset.forName(encoding).equals(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }
}
