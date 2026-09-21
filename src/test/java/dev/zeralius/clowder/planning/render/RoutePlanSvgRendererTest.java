package dev.zeralius.clowder.planning.render;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zeralius.clowder.common.geo.EuclideanDistanceProvider;
import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.planning.model.DistanceMatrix;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.model.StopSpec;
import dev.zeralius.clowder.planning.model.VehicleSpec;
import dev.zeralius.clowder.planning.solver.Route;
import dev.zeralius.clowder.planning.solver.Solution;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The output is parsed as XML rather than grepped: a README that shows a broken image is worse than
 * one that shows none.
 */
class RoutePlanSvgRendererTest {

    private static final List<LatLon> POINTS =
            List.of(LatLon.of(0d, 0d), LatLon.of(0d, 10d), LatLon.of(0d, 30d), LatLon.of(20d, 60d));

    private final RoutePlanSvgRenderer renderer = new RoutePlanSvgRenderer();

    private static ProblemInstance instance() {
        var matrix = DistanceMatrix.from(POINTS, new EuclideanDistanceProvider());
        var stops = List.of(
                new StopSpec(100L, 1, 0L, 0L, 0, 0, 1000),
                new StopSpec(200L, 2, 0L, 0L, 0, 0, 1000),
                new StopSpec(300L, 3, 0L, 0L, 0, 0, 1000));
        var vehicles = List.of(
                new VehicleSpec(1L, 100L, 100L, 0, 1000, 0),
                new VehicleSpec(2L, 100L, 100L, 0, 1000, 0),
                new VehicleSpec(3L, 100L, 100L, 0, 1000, 0));
        return new ProblemInstance(vehicles, stops, matrix, 0);
    }

    private static Solution twoRoutesAndAnIdleVehicle() {
        return new Solution(
                List.of(
                        new Route(1L, new int[] {0, 1}, 60L, 60),
                        new Route(2L, new int[] {2}, 120L, 120),
                        new Route(3L, new int[0], 0L, 0)),
                new int[0],
                180L);
    }

    @Test
    void producesWellFormedSvgWithOnePolylinePerUsedVehicle() throws Exception {
        var svg = renderer.render(twoRoutesAndAnIdleVehicle(), instance(), POINTS, 0);
        var document = parse(svg);

        assertThat(document.getDocumentElement().getTagName()).isEqualTo("svg");
        assertThat(document.getDocumentElement().getAttribute("viewBox")).isEqualTo("0 0 800 800");
        // Two routes drawn; the idle vehicle draws nothing.
        assertThat(document.getElementsByTagName("polyline").getLength()).isEqualTo(2);
        // One dot per stop, never one for the depot.
        assertThat(document.getElementsByTagName("circle").getLength()).isEqualTo(3);
        // Background plus the depot marker.
        assertThat(document.getElementsByTagName("rect").getLength()).isEqualTo(2);
    }

    @Test
    void everyPolylineStartsAndEndsAtTheDepot() throws Exception {
        var svg = renderer.render(twoRoutesAndAnIdleVehicle(), instance(), POINTS, 0);
        var polylines = parse(svg).getElementsByTagName("polyline");

        for (var i = 0; i < polylines.getLength(); i++) {
            var points = ((Element) polylines.item(i)).getAttribute("points").trim().split(" ");
            assertThat(points[0]).as("polyline %d starts at the depot", i).isEqualTo(points[points.length - 1]);
            assertThat(points[0]).isEqualTo("40.0,760.0"); // the depot is the south-west corner of the data
        }
    }

    /** Longitude 60, latitude 20 is the north-east-most point: right of centre and, with y flipped, above the depot. */
    @Test
    void projectsNorthUpAndEastRight() throws Exception {
        var svg = renderer.render(twoRoutesAndAnIdleVehicle(), instance(), POINTS, 0);
        var circles = parse(svg).getElementsByTagName("circle");

        var farthest = (Element) circles.item(2);
        assertThat(Double.parseDouble(farthest.getAttribute("cx"))).isEqualTo(760.0);
        assertThat(Double.parseDouble(farthest.getAttribute("cy"))).isLessThan(760.0);
    }

    @Test
    void routesTakeDistinctColoursFromThePalette() throws Exception {
        var svg = renderer.render(twoRoutesAndAnIdleVehicle(), instance(), POINTS, 0);
        var polylines = parse(svg).getElementsByTagName("polyline");

        var first = ((Element) polylines.item(0)).getAttribute("stroke");
        var second = ((Element) polylines.item(1)).getAttribute("stroke");
        assertThat(first).matches("#[0-9a-f]{6}");
        assertThat(second).matches("#[0-9a-f]{6}").isNotEqualTo(first);
    }

    /** Every point on top of every other must not divide by zero; it just draws everything in one spot. */
    @Test
    void survivesAnInstanceWithNoSpatialExtent() throws Exception {
        var samePlace = List.of(LatLon.of(5d, 5d), LatLon.of(5d, 5d));
        var matrix = DistanceMatrix.from(samePlace, new EuclideanDistanceProvider());
        var instance = new ProblemInstance(
                List.of(new VehicleSpec(1L, 100L, 100L, 0, 1000, 0)),
                List.of(new StopSpec(100L, 1, 0L, 0L, 0, 0, 1000)),
                matrix,
                0);
        var solution = new Solution(List.of(new Route(1L, new int[] {0}, 0L, 0)), new int[0], 0L);

        var svg = renderer.render(solution, instance, samePlace, 0);

        assertThat(svg).doesNotContain("NaN").doesNotContain("Infinity");
        assertThat(parse(svg).getElementsByTagName("polyline").getLength()).isEqualTo(1);
    }

    private static Document parse(String svg) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }
}
