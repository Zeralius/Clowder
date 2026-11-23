package dev.zeralius.clowder.common.error;

public class NotFoundException extends DomainException {

    public NotFoundException(String type, Object id) {
        super("%s with id '%s' was not found".formatted(type, id));
    }
}
