package dev.zeralius.clowder.security;

/**
 * What other modules may know about who is calling. The one door, same as the other facades.
 *
 * <p>Deliberately not "give me the Authentication": a module that wants to check whether this driver
 * owns this plan should not have to know that Spring Security exists, let alone how a claim is named.
 */
public interface CurrentUser {

    /**
     * The authenticated caller.
     *
     * @throws dev.zeralius.clowder.common.error.ConflictException when there is no authenticated
     *     caller, which can only happen if an endpoint was left out of the filter chain — a
     *     programming error, not something a client can cause
     */
    AuthenticatedUser require();
}
