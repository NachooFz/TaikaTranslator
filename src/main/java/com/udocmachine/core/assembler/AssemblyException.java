package com.udocmachine.core.assembler;

public class AssemblyException extends Exception {
    public AssemblyException(String message) {
        super(message);
    }

    public AssemblyException(String message, Throwable cause) {
        super(message, cause);
    }
}
