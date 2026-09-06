package dev.zeralius.clowder.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Public pagination envelope. Deliberately not Spring's {@code Page}, whose serialised form is an
 * internal detail.
 *
 * <p>Offset pagination is fine at this scale; cursor pagination is noted as future work in the
 * README rather than pretended at here.
 *
 * <p>{@code <T>} after the class name is a <em>type parameter</em>: a placeholder for a type the
 * caller chooses. It is not a real type — it is filled in at each use site, so
 * {@code PageResponse<VehicleResponse>} is this record with every {@code T} replaced by
 * {@code VehicleResponse}, giving a {@code List<VehicleResponse> content}. One declaration, any
 * element type, and the compiler still catches you putting a {@code DriverResponse} in it.
 *
 * @param <T> the type of one item in {@link #content()}
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    /**
     * Maps a Spring {@code Page} of entities into a page of DTOs.
     *
     * <p>The {@code <E, D>} before the return type declares two type parameters <em>belonging to this
     * method</em>, not to the record. A {@code static} method cannot use the class's {@code T}: {@code T}
     * is chosen per instance, and a static method has no instance to read it from. So the method
     * declares its own, and they are only in scope for the length of this one call.
     *
     * <p>Read the signature left to right: "for any two types E and D, given a page of E and a way to
     * turn one E into one D, produce a page of D." The compiler infers both from the arguments — call
     * {@code PageResponse.from(vehiclePage, VehicleMapper::toResponse)} and E becomes {@code Vehicle},
     * D becomes {@code VehicleResponse}. You never write them at the call site.
     *
     * <p>{@code Function<E, D>} is the same idea one level down: a function taking an {@code E} and
     * returning a {@code D}. And {@code new PageResponse<>(...)} uses the diamond — the compiler already
     * knows it must be a {@code PageResponse<D>} from the return type, so repeating it would be noise.
     *
     * @param <E> the entity type on the way in
     * @param <D> the DTO type on the way out
     * @param mapper runs inside the caller's transaction; this is what stops entities escaping
     */
    public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}