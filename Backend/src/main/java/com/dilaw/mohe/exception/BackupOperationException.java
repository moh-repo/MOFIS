package com.dilaw.mohe.exception;

/** Thrown when a backup/restore operation fails; handled by GlobalExceptionHandler. */
public class BackupOperationException extends RuntimeException {

    public BackupOperationException(String message) {
        super(message);
    }

    public BackupOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
