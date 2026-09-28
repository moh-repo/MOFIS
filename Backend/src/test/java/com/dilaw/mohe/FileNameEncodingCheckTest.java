package com.dilaw.mohe;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FileNameEncodingCheckTest {

    @Test
    void utf8SpellingsAreAccepted() {
        assertThat(FileNameEncodingCheck.isUnicode("UTF-8")).isTrue();
        assertThat(FileNameEncodingCheck.isUnicode("utf8")).isTrue();
    }

    @Test
    void posixAndUnknownEncodingsAreRefused() {
        assertThat(FileNameEncodingCheck.isUnicode("ANSI_X3.4-1968")).isFalse();
        assertThat(FileNameEncodingCheck.isUnicode("US-ASCII")).isFalse();
        assertThat(FileNameEncodingCheck.isUnicode("no-such-charset")).isFalse();
        assertThat(FileNameEncodingCheck.isUnicode(null)).isFalse();
    }

    /** The surefire fork is given a UTF-8 locale in pom.xml; an Arabic path must resolve. */
    @Test
    void testJvmCanNameArabicFiles() {
        assertThat(FileNameEncodingCheck.isUnicode(System.getProperty("sun.jnu.encoding"))).isTrue();
        assertThat(Path.of("مصر 20253.xlsx").getFileName().toString()).isEqualTo("مصر 20253.xlsx");
    }
}
