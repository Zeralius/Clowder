# Benchmarks

## Instance files

`solomon-100/` holds all 56 of Solomon's 100-customer VRPTW instances. They are committed so the
results here can be reproduced: C101 to C109, C201 to C208, R101 to R112, R201 to R211, RC101 to
RC108, and RC201 to RC208.

**Where they came from.** Solomon's own site (`web.cba.neu.edu/~msolomon/problems.htm`) was
unreachable and SINTEF's `solomon-100.zip` turned out to be incomplete, so these are the copies in
<https://github.com/irb-lab/py-ga-vrptw> under `data/text/`. I checked them against the canonical
values before using them: R101 has 25 vehicles of capacity 200, a depot at (35, 35) with a horizon
of 230, and customer 1 reads `41 49 10 161 171 10`. All 56 parse, and all 56 solve to a distance
near their published best-known, eight of them exactly. These are Solomon's research data, not mine.

Any `.txt` file anywhere under `solomon-100/` gets picked up, and the instance name is read from the
first line of the file rather than from the filename. The expected shape is the published one:

```text
RC101

VEHICLE
NUMBER     CAPACITY
  25         200

CUSTOMER
CUST NO.   XCOORD.   YCOORD.   DEMAND   READY TIME   DUE DATE   SERVICE TIME

    0      40        50         0          0          240          0
    1      25        85        20        145          175         10
```

The parser finds the `NUMBER` and `CUST NO.` marker lines and then splits on runs of whitespace, so
the exact spacing and the line endings do not matter. Customer 0 is the depot, and its due date is
the planning horizon.

## `best-known.csv`

Best-known solutions under the standard hierarchical objective, which is to minimise vehicle count
first and then distance.

**Source:** SINTEF's VRPTW benchmark pages for the Solomon 100-customer instances
(<https://www.sintef.no/projectweb/top/vrptw/solomon-benchmark/100-customers/>), as published there
on a page dated 2008-04-18. SINTEF credits the individual solutions to Rochat & Taillard (1995),
Mester, Bräysy & Dullaert (2005), Homberger & Gehring (1999), Shaw (1997, 1998), Berger, Barkaoui &
Bräysy (2001), Li, Lim & Huang (2001), Taillard et al. (1997) and others. It also notes corrected
double-precision values supplied by Bauke Conijn and David Mester, and a correction to R111 by
Victor Allis.

These are reference values and not mine. A wrong number in here silently poisons every gap in the
results table, so only change them against a source you can cite, and say which one.

## `results.csv`

Generated, and committed on purpose, because regenerating benchmarks on every CI run makes builds
slow and flaky. Rebuild it deliberately:

```bash
./mvnw -o -q compile
java -cp target/classes dev.zeralius.clowder.planning.bench.BenchmarkMain \
  benchmarks/solomon-100 benchmarks/best-known.csv benchmarks/results.csv 5
```

The last argument is the per-instance wall-clock budget in seconds. Note down which machine the
numbers came from when you commit them, because these are timings and timings are not portable.

A positive `gapPercent` means worse than best-known. The comparison is not quite like for like,
because travel times here are rounded to whole minutes while the published results use real-valued
times. The rounding makes time windows harder to satisfy rather than easier, so it cannot flatter
these numbers.
