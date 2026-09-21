package dev.zeralius.clowder.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class SolomonInstanceParserTest {

    private final SolomonInstanceParser parser = new SolomonInstanceParser();

    private static Reader fixture() {
        var stream = Objects.requireNonNull(
                SolomonInstanceParserTest.class.getResourceAsStream("/solomon/MINI.txt"), "fixture missing");
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    @Test
    void readsTheInstanceHeader() {
        var instance = parser.parse(fixture());

        assertThat(instance.name()).isEqualTo("MINI");
        assertThat(instance.vehicleCount()).isEqualTo(3);
        assertThat(instance.capacity()).isEqualTo(200L);
    }

    @Test
    void treatsCustomerZeroAsTheDepotAndExcludesItFromCustomers() {
        var instance = parser.parse(fixture());

        assertThat(instance.depot().id()).isZero();
        assertThat(instance.depot().x()).isEqualTo(35.0);
        assertThat(instance.depot().demand()).isZero();
        assertThat(instance.customers()).hasSize(3).noneMatch(c -> c.id() == 0);
    }

    @Test
    void readsTheHorizonFromTheDepotDueDate() {
        assertThat(parser.parse(fixture()).horizonMinutes()).isEqualTo(230);
    }

    @Test
    void readsCustomerRows() {
        var first = parser.parse(fixture()).customers().get(0);

        assertThat(first.id()).isEqualTo(1);
        assertThat(first.x()).isEqualTo(41.0);
        assertThat(first.y()).isEqualTo(49.0);
        assertThat(first.demand()).isEqualTo(10L);
        assertThat(first.readyTime()).isEqualTo(161);
        assertThat(first.dueTime()).isEqualTo(171);
        assertThat(first.serviceTime()).isEqualTo(10);
    }

    @Test
    void toleratesIntegerFieldsWrittenWithoutDecimals() {
        var text =
                """
                TERSE

                VEHICLE
                NUMBER     CAPACITY
                  2          100

                CUSTOMER
                CUST NO.  XCOORD.  YCOORD.  DEMAND  READY TIME  DUE DATE  SERVICE TIME

                  0   40   50   0    0   100   0
                  1   45   55   5   10    60   3
                """;

        var instance = parser.parse(new StringReader(text));

        assertThat(instance.capacity()).isEqualTo(100L);
        assertThat(instance.customers().get(0).serviceTime()).isEqualTo(3);
    }

    @Test
    void rejectsAnInstanceWithNoDepotRow() {
        var text =
                """
                BROKEN

                VEHICLE
                NUMBER     CAPACITY
                  2          100

                CUSTOMER
                CUST NO.  XCOORD.  YCOORD.  DEMAND  READY TIME  DUE DATE  SERVICE TIME

                  1   45   55   5   10    60   3
                """;

        assertThatThrownBy(() -> parser.parse(new StringReader(text)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customer 0");
    }

    @Test
    void rejectsATruncatedCustomerRow() {
        var text =
                """
                BROKEN

                VEHICLE
                NUMBER     CAPACITY
                  2          100

                CUSTOMER
                CUST NO.  XCOORD.  YCOORD.  DEMAND  READY TIME  DUE DATE  SERVICE TIME

                  0   40   50   0    0   100   0
                  1   45   55
                """;

        assertThatThrownBy(() -> parser.parse(new StringReader(text)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7 fields");
    }
}
