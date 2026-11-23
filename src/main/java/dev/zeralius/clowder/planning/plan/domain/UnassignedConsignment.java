package dev.zeralius.clowder.planning.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A consignment the solver could not route, recorded against the run that could not route it.
 *
 * <p>The reference is copied rather than joined for the same reason {@code plan_stop} copies it: this
 * is a record of what happened, and it must not change when the consignment does.
 */
@Embeddable
public class UnassignedConsignment {

    @Column(name = "consignment_id", nullable = false)
    private Long consignmentId;

    @Column(nullable = false, length = 32)
    private String reference;

    /** For JPA. */
    protected UnassignedConsignment() {}

    public UnassignedConsignment(Long consignmentId, String reference) {
        this.consignmentId = consignmentId;
        this.reference = reference;
    }

    public Long getConsignmentId() {
        return consignmentId;
    }

    public String getReference() {
        return reference;
    }
}
