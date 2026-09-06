package dev.zeralius.clowder.common.error;

public class NotFoundException extends DomainException {
    public NotFoundException(String type, Object id) {
        super(String.format("No such domain object with id '%s'.", id));
    }
}
