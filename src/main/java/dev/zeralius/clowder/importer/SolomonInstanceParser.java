package dev.zeralius.clowder.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the Solomon VRPTW instance format:
 *
 * <pre>
 * R101
 *
 * VEHICLE
 * NUMBER     CAPACITY
 *   25         200
 *
 * CUSTOMER
 * CUST NO.  XCOORD.  YCOORD.  DEMAND  READY TIME  DUE DATE  SERVICE TIME
 *
 *     0      35.00    35.00    0.00     0.00       230.00      0.00
 *     1      41.00    49.00   10.00   161.00       171.00     10.00
 * </pre>
 *
 * <p>Not a fixed-width parser: the published files are aligned by eye, and mirrors differ in spacing
 * and line endings. Locating the two marker lines and splitting on whitespace is shorter and more
 * robust.
 *
 * <p>Pure: no Spring, no database, no file system. Takes a {@link Reader} so it is trivially testable
 * against a string — the interesting logic never knows where its input came from.
 */
public final class SolomonInstanceParser {

    private static final String VEHICLE_MARKER = "NUMBER";
    private static final String CUSTOMER_MARKER = "CUST NO.";

    public SolomonInstance parse(Reader reader) {
        var lines = readNonBlankLines(reader);

        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Instance is empty");
        }

        var name = lines.get(0);

        var vehicleMarker = indexOfLineStartingWith(lines, VEHICLE_MARKER);
        var fleet = splitOnWhitespace(lines.get(vehicleMarker + 1));
        if (fleet.length < 2) {
            throw new IllegalArgumentException("Expected 'count capacity' after the " + VEHICLE_MARKER
                    + " header, got: " + lines.get(vehicleMarker + 1));
        }
        var vehicleCount = parseIntLenient(fleet[0], "vehicle count");
        var capacity = parseLongLenient(fleet[1], "vehicle capacity");

        var customerMarker = indexOfLineContaining(lines, CUSTOMER_MARKER);

        SolomonCustomer depot = null;
        var customers = new ArrayList<SolomonCustomer>();

        for (var i = customerMarker + 1; i < lines.size(); i++) {
            var customer = parseCustomer(lines.get(i));
            if (customer.id() == 0) {
                depot = customer;
            } else {
                customers.add(customer);
            }
        }

        if (depot == null) {
            throw new IllegalArgumentException("Instance has no customer 0 (the depot)");
        }
        if (customers.isEmpty()) {
            throw new IllegalArgumentException("Instance has no customers");
        }

        return new SolomonInstance(name, vehicleCount, capacity, depot, customers);
    }

    private static SolomonCustomer parseCustomer(String line) {
        var f = splitOnWhitespace(line);
        if (f.length < 7) {
            throw new IllegalArgumentException("Expected 7 fields in customer row, got " + f.length + ": " + line);
        }
        return new SolomonCustomer(
                parseIntLenient(f[0], "customer id"),
                Double.parseDouble(f[1]),
                Double.parseDouble(f[2]),
                parseLongLenient(f[3], "demand"),
                parseIntLenient(f[4], "ready time"),
                parseIntLenient(f[5], "due date"),
                parseIntLenient(f[6], "service time"));
    }

    private static List<String> readNonBlankLines(Reader reader) {
        try (var buffered = new BufferedReader(reader)) {
            var lines = new ArrayList<String>();
            String line;
            while ((line = buffered.readLine()) != null) {
                var trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
            return lines;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read Solomon instance", e);
        }
    }

    private static String[] splitOnWhitespace(String line) {
        return line.trim().split("\\s+");
    }

    private static int indexOfLineStartingWith(List<String> lines, String prefix) {
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Instance has no line starting with '" + prefix + "'");
    }

    private static int indexOfLineContaining(List<String> lines, String needle) {
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Instance has no line containing '" + needle + "'");
    }

    /** Files vary between {@code 230} and {@code 230.00} for the same field. */
    private static int parseIntLenient(String token, String what) {
        return Math.toIntExact(parseLongLenient(token, what));
    }

    private static long parseLongLenient(String token, String what) {
        try {
            return Math.round(Double.parseDouble(token));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Could not read " + what + " from '" + token + "'", e);
        }
    }
}
