package dev.zeralius.clowder.common.error;

public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super(message);
    }
}
