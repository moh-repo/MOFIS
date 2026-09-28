package com.dilaw.mohe.exception;

/**
 * Raised when an encrypted file fails integrity verification or cannot be decrypted.
 */
public class FileEncryptionException extends RuntimeException {

    public FileEncryptionException(String message) {
        super(message);
    }

    public FileEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
