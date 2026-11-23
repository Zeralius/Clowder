package dev.zeralius.clowder.planning.render;

import dev.zeralius.clowder.common.geo.LatLon;
import dev.zeralius.clowder.planning.model.ProblemInstance;
import dev.zeralius.clowder.planning.solver.Route;
import dev.zeralius.clowder.planning.solver.Solution;
import java.util.List;
import java.util.Locale;

/**
 * Renders a solved instance as standalone SVG: depot marker, one coloured polyline per route, stops as
 * dots. Server-generated SVG needs no JavaScript, no build step and no dependency, and it renders in
 * a GitHub README, which a canvas would not.
 *
 * <p>Pure string building — no Spring, no I/O, no dependency. Same discipline as the rest of {@code
 * planning}.
 *
 * <p>Takes the point list separately because {@link ProblemInstance} carries only the matrix, not
 * the coordinates it was built from; the caller that built the matrix still has them.
 */
public final class RoutePlanSvgRenderer {

    /** Colour-blind-safe qualitative palette (Paul Tol's "bright"); routes cycle through it. */
    private static final List<String> PALETTE =
            List.of("#4477aa", "#ee6677", "#228833", "#ccbb44", "#66ccee", "#aa3377", "#bbbbbb", "#ee8866");

    private static final int SIZE = 800;
    private static final int PADDING = 40;

    /**
     * @param points the coordinates the instance's matrix was built from, in matrix-index order
     * @param depotIndex the matrix row of the depot
     */
    public String render(Solution solution, ProblemInstance instance, List<LatLon> points, int depotIndex) {
        final var bounds = Bounds.of(points);
        final var svg = new StringBuilder(8192);

        svg.append(String.format(
                Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" width=\"%d\" height=\"%d\">%n",
                SIZE,
                SIZE,
                SIZE,
                SIZE));
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");

        var colour = 0;
        for (var route : solution.routes()) {
            if (route.isEmpty()) {
                continue;
            }
            svg.append(polyline(route, instance, points, depotIndex, bounds, PALETTE.get(colour % PALETTE.size())));
            colour++;
        }

        for (var i = 0; i < points.size(); i++) {
            if (i == depotIndex) {
                continue;
            }
            final var p = bounds.project(points.get(i));
            svg.append(String.format(
                    Locale.ROOT, "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"3\" fill=\"#333333\"/>%n", p[0], p[1]));
        }

        final var depot = bounds.project(points.get(depotIndex));
        svg.append(String.format(
                Locale.ROOT,
                "<rect x=\"%.1f\" y=\"%.1f\" width=\"12\" height=\"12\" fill=\"#000000\"/>%n",
                depot[0] - 6,
                depot[1] - 6));

        svg.append("</svg>\n");
        return svg.toString();
    }

    private String polyline(
            Route route, ProblemInstance instance, List<LatLon> points, int depotIndex, Bounds bounds, String colour) {

        final var path = new StringBuilder();
        appendPoint(path, bounds.project(points.get(depotIndex)));
        for (var position : route.stopPositions()) {
            appendPoint(
                    path, bounds.project(points.get(instance.stopAt(position).index())));
        }
        appendPoint(path, bounds.project(points.get(depotIndex)));

        return String.format(
                Locale.ROOT,
                "<polyline points=\"%s\" fill=\"none\" stroke=\"%s\" stroke-width=\"1.5\" stroke-opacity=\"0.85\"/>%n",
                path.toString().trim(),
                colour);
    }

    private static void appendPoint(StringBuilder path, double[] xy) {
        path.append(String.format(Locale.ROOT, "%.1f,%.1f ", xy[0], xy[1]));
    }

    /** Scales the instance's coordinate range into the viewport, preserving aspect ratio. */
    private record Bounds(double minX, double maxX, double minY, double maxY) {

        static Bounds of(List<LatLon> points) {
            var minX = Double.MAX_VALUE;
            var maxX = -Double.MAX_VALUE;
            var minY = Double.MAX_VALUE;
            var maxY = -Double.MAX_VALUE;

            for (var point : points) {
                final var x = point.longitude().doubleValue();
                final var y = point.latitude().doubleValue();
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
            return new Bounds(minX, maxX, minY, maxY);
        }

        double[] project(LatLon point) {
            final var span = Math.max(Math.max(maxX - minX, maxY - minY), 1e-9);
            final var scale = (SIZE - 2.0 * PADDING) / span;
            final var x = PADDING + (point.longitude().doubleValue() - minX) * scale;
            // SVG y grows downward; flip so north is up.
            final var y = SIZE - PADDING - (point.latitude().doubleValue() - minY) * scale;
            return new double[] {x, y};
        }
    }
}
