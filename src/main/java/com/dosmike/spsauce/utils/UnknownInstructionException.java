package com.dosmike.spsauce.utils;

public class UnknownInstructionException extends RuntimeException {

    public UnknownInstructionException() {
    }

    public UnknownInstructionException(String message) {
        super(message);
    }

    public UnknownInstructionException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnknownInstructionException(Throwable cause) {
        super(cause);
    }
}
