# Benchmarks

## Instance files

`solomon-100/` holds all 56 of M. M. Solomon's 100-customer VRPTW instances, committed so the
results below are reproducible: C101–C109, C201–C208, R101–R112, R201–R211, RC101–RC108,
RC201–RC208.

**Provenance.** Solomon's own site (`web.cba.neu.edu/~msolomon/problems.htm`) was unreachable and
SINTEF's `solomon-100.zip` turned out to be incomplete, so these came from the copy in
<https://github.com/irb-lab/py-ga-vrptw> under `data/text/`. They were checked against the canonical
values before use — R101 has 25 vehicles of capacity 200, a depot at (35, 35) with horizon 230, and
customer 1 reading `41 49 10 161 171 10` — and every one of the 56 parses and solves to a distance
within a sane margin of its published best-known, eight of them exactly. These are Solomon's
research data, not this project's.

Any `.txt` file anywhere under `solomon-100/` is picked up, and the instance's real name comes from
the first line of the file rather than the filename. The expected shape is the published one:

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

The parser locates the `NUMBER` and `CUST NO.` marker lines and splits on runs of whitespace, so
spacing and line endings do not matter. Customer 0 is the depot; its due date is the planning
horizon.

## `best-known.csv`

Best-known solutions under the standard hierarchical objective — minimise vehicle count first, then
distance.

**Source:** SINTEF's VRPTW benchmark pages for the Solomon 100-customer instances
(<https://www.sintef.no/projectweb/top/vrptw/solomon-benchmark/100-customers/>), as published there,
page dated 2008-04-18. SINTEF credits the individual solutions to Rochat & Taillard (1995), Mester,
Bräysy & Dullaert (2005), Homberger & Gehring (1999), Shaw (1997, 1998), Berger, Barkaoui & Bräysy
(2001), Li, Lim & Huang (2001), Taillard et al. (1997) and others, and notes corrected
double-precision values supplied by Bauke Conijn and David Mester, and a correction to R111 by
Victor Allis.

These are reference values, not ours. A wrong number here silently poisons every gap in the results
table, so change them only against a citable source and say which one.

## `results.csv`

Generated, and committed on purpose — regenerating benchmarks on every CI run makes builds slow and
flaky. Rebuild it deliberately:

```bash
./mvnw -o -q compile
java -cp target/classes dev.zeralius.clowder.planning.bench.BenchmarkMain \
  benchmarks/solomon-100 benchmarks/best-known.csv benchmarks/results.csv 5
```

The last argument is the per-instance wall-clock budget in seconds. Note the machine the numbers
came from when you commit them: these are timings, and timings are not portable.

A positive `gapPercent` means worse than best-known. The comparison is not quite like-for-like —
travel times here are rounded to whole minutes while the published results use real-valued times —
and the rounding makes time windows harder, not easier, so it cannot flatter these numbers.
