# Multi-Stop Route Planner

Works out the efficient order to drive to several places, then hands the route to Google Maps or Waze.

**Status: early development.** The first usable version is being built in the open. Star or watch the repository to hear when it lands.

Built and tested in Auckland NZ, but it works anywhere OpenStreetMap has road data.

## The problem

When you have three or more places to visit, the hard part is not the driving. It is working out which one to go to first. Get the order wrong and you waste time, fuel and money.

The usual navigation apps do not solve this:

- **Waze** lets you add only one stop to a route.
- **Google Maps** lets you add several stops, but you have to drag them into order yourself and compare the results by hand.

With five stops there are 120 possible orders. With ten stops there are more than three million. Nobody can check those by hand.

## The plan

Develop an application that can accept a starting point and a list of places. It works out the order that makes the trip efficient, then hands each leg to Google Maps or Waze for the actual driving. Choosing a good order is where most of the saving comes from, because it cuts distance, fuel use and driving time at the same time.

- Efficient stop order for a list of addresses
- A stop that has to keep its turn, such as the one that must come first, stays there while the rest are arranged around it
- Type an address or a business name and have it found, nearest first, instead of hunting for it on the map
- Real driving distances from OpenStreetMap, not straight lines
- Fuel estimates from the road (distance, speed limits, hills) and the vehicle (engine, size, load)
- A choice between the fastest route and the most fuel-efficient one, showing the real difference in minutes and litres

## Stack

| Part | Choice |
| --- | --- |
| API | Java 25, Spring Boot 4 |
| Routing | GraphHopper with OpenStreetMap data |
| Storage | PostgreSQL with PostGIS |
| Web app | React with Leaflet |

Runs on free, open data, so anyone can host their own copy without paying for an API key.

## Running it

You need Java 25, pnpm and PostgreSQL with PostGIS. On a Mac, [Postgres.app](https://postgresapp.com) includes PostGIS:

```bash
createdb routeplanner
```

Two halves. The API, which applies its own database migrations on start:

```bash
cd backend && ./mvnw spring-boot:run
```

The map screen, in a second terminal:

```bash
cd frontend && pnpm install && pnpm dev
```

Open http://localhost:5173. If you let the browser share your location, the trip starts from where you are. Otherwise click your start, then each stop, and plan the route. A pin in the wrong spot can be dragged. A stop that has to keep its turn has a "keep" button beside it. Once the data below is loaded you can type them instead, and the nearest match comes first.

That uses straight-line distances, which need no setup. For real driving distances, download the New Zealand map extract once, 385 MB, and start the API with the routing provider switched on:

```bash
curl -o backend/data/new-zealand-latest.osm.pbf https://download.geofabrik.de/australia-oceania/new-zealand-latest.osm.pbf
```

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.arguments=--routeplanner.routing.provider=graphhopper
```

The first start builds a routing graph and takes a few minutes, then loads in under a second after that. Neither the map file nor the graph goes into git.

### Address search

Typing an address needs the LINZ address data, which is free but not redistributable in this repository. Download **NZ Addresses** from [the LINZ Data Service](https://data.linz.govt.nz/layer/123113-nz-addresses/) as CSV in WGS84, unzip it into `backend/data/`, then load it once:

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.arguments="--routeplanner.addresses.import-enabled=true"
```

That imports about 2.4 million addresses in about a minute using PostgreSQL `COPY`. Without it everything still works, you just click the map instead of typing.

### Business names

Addresses alone cannot find "New World Remuera", because LINZ records where addresses are, not what stands on them. Names come from [Overture Maps](https://overturemaps.org) places, which are free and need no account. Install [DuckDB](https://duckdb.org), then export New Zealand, about 200,000 places in a 28 MB file:

```bash
cd backend && mkdir -p data && duckdb -f scripts/export-overture-places.sql
```

Load it:

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.arguments="--routeplanner.poi.import-enabled=true"
```

Import the addresses first, because each place takes its suburb from the nearest address. Overture publishes every month. Run both commands again to pick up a new release: places are updated, added and removed to match the file.

### One app, to use it from a phone

The two halves above are for working on the code. To use the planner, build the map screen into the API, so that one program serves both on one port:

```bash
cd backend && ./mvnw -Pwith-screen -DskipTests package
```

```bash
cd backend && java -jar target/route-planner-0.0.1-SNAPSHOT.jar --routeplanner.routing.provider=graphhopper
```

Open http://localhost:8080. Leave the last argument off for straight-line distances. The screen in the jar is as old as the build, so while working on the code keep using port 5173.

The app has no login, so it answers only the machine it runs on. Nothing else on the network can connect to it. To use it from a phone, put a private tunnel in front of port 8080: one that only your own devices can use, and that gives the app an https address. A phone shares its location only with an https page.

Do not put the app on an address that anyone can open. Do not point a tunnel at the two development servers either. They are not built to face other people.

## Roadmap

- [x] 1. REST API that returns the efficient stop order, using straight-line distances
- [ ] 2. Address search and saved places, in PostgreSQL
- [x] 3. Real driving distances from GraphHopper
- [x] 4. Map screen, first release
- [ ] 5. Vehicle information: engine, size and load
- [ ] 6. Fuel-efficient route option, using that vehicle information

## Later

Once the single driver version is stable:

- Several drivers and vehicles in one plan
- Delivery time windows, for stops that have to happen between set hours
- Vehicle capacity, so a van is not given more than it can carry
- Fleet planning with jsprit

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Copyright 2026 Gerald Guledew. Apache License 2.0, see [LICENSE](LICENSE).

## Data

- Map and roads: © OpenStreetMap contributors, Open Database License
- NZ addresses: Toitū Te Whenua Land Information New Zealand, CC BY 4.0
- Business names: Overture Maps Foundation, overturemaps.org. Places from Meta and Microsoft under CDLA Permissive 2.0, from Foursquare under Apache 2.0 (Copyright 2024 Foursquare Labs, Inc.) and from AllThePlaces under CC0
