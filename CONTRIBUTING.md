# Contributing

Contributions are welcome. Open an issue before starting anything large, so two people do not build the same thing.

## Ways to help

- Test it in your own city once the first release is out, and report what breaks
- Improve the fuel model, vehicle settings or routing setup
- Tests, documentation, worked examples

## Setup

Java 25 and Git. The build uses Maven through the `./mvnw` wrapper, so Maven itself does not need to be installed. PostgreSQL arrives at step 2 of the roadmap.

```bash
cd backend && ./mvnw verify
```

That runs on straight-line distances and needs nothing else. For real driving distances, download the 385 MB map extract, which stays out of git, and switch the provider in `application.yaml`:

```bash
curl -o backend/data/new-zealand-latest.osm.pbf https://download.geofabrik.de/australia-oceania/new-zealand-latest.osm.pbf
```

The road tests skip themselves when that file is absent, so a clean checkout stays green.

## Pull requests

1. Fork, then branch off `main`.
2. One thing per pull request.
3. Include tests. Routing bugs are easy to miss by eye.
4. Say what changed and why, and link the issue.

## Code style

- Standard Spring Boot layout, Java 25
- JUnit 5 for tests
- Keep the routing and ordering logic free of framework annotations, so it can be tested on its own

## Bug reports

Include the start point and stops you used, what you expected, and what happened. Coordinates or addresses make it reproducible.

## License

Contributions are released under the Apache License 2.0.
