package dev.zeralius.clowder.common.error;

/**
 * The caller is who they say they are and still may not do this.
 *
 * <p>Distinct from {@link ConflictException} because the cause is different and so is the fix: a 409
 * means try something else, a 403 means ask someone else. Distinct from an authentication failure
 * because retrying with the same token will never help.
 */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(message);
    }
}
