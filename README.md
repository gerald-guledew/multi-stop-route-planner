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

## Roadmap

- [ ] 1. REST API that returns the efficient stop order, using straight-line distances
- [ ] 2. Saved places in PostgreSQL, with database migrations
- [ ] 3. Real driving distances from GraphHopper
- [ ] 4. Map screen, first release
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
