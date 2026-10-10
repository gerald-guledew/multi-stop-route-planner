# Architecture

How the pieces fit together, and the two decisions that keep the later steps cheap.

## Request flow

```mermaid
flowchart TD
    Client["Client: curl, tests, web app later"] -->|"POST /api/v1/routes/optimize"| Controller[RouteController]
    Controller --> Service[RoutePlanningService]
    Service --> Provider["TravelMatrixProvider (interface)"]
    Service --> Solver["RouteSolver (interface)"]
    Provider --> Haversine["Haversine, straight line"]
    Provider --> GraphHopper["GraphHopper, real roads"]
    Solver --> Brute["Brute force, every order"]
    Solver -.-> Heuristic["Nearest neighbour and 2-opt, later"]
```

Dotted lines are not built yet. Exactly one distance provider is active, chosen by `routeplanner.routing.provider` in `application.yaml`.

1. The controller takes JSON, validates it, and turns it into domain objects.
2. The service asks a matrix provider for travel figures between every pair of places.
3. The service turns those figures into a cost matrix. Today cost means distance.
4. The solver returns the cheapest order it can find.
5. The service builds the answer: the ordered stops, each leg, and the totals.

## Components

| Package | Responsibility |
| --- | --- |
| `api` | HTTP only. Controller, request and response records, validation, error handling |
| `planning` | The domain. Cost matrices, solvers, the resulting plan. Plain Java, no Spring annotations |
| `distance` | Where travel figures come from. The provider interface and its implementations |
| `address` | LINZ addresses: the table and its importer |
| `poi` | Named places from Overture Maps: the table and its importer |
| `search` | Finds addresses and named places from what was typed. The `PlaceSearch` interface and its PostgreSQL implementation |

Nothing in `planning` knows about HTTP, and nothing in `api` knows how distances are calculated.

## The two seams

**Travel figures arrive through an interface.** The service never calls haversine or GraphHopper itself. At step 3 a second implementation replaces straight lines with real driving distances, and the service, the solver and the controller stay untouched.

The interface hands back a whole matrix rather than one pair at a time, because routing engines calculate many-to-many distances in a single pass. Asking pair by pair would be far slower once real roads arrive.

**The solver minimises a cost matrix without knowing what cost means.** Distance fills it today. Seconds or litres can fill it later. That turns "fastest or most fuel-efficient" into a decision about how the matrix is built, not a rewrite of the algorithm.

**A stop kept in its place is a rule about the order, not about the cost.** The solver is told which stops must be visited at the turn they were entered in, and it only ever builds the orders that respect that. It still knows nothing about why. Keeping a stop also makes the work smaller: ten stops with three kept is 5,040 orders to compare rather than 3.6 million.

## Place search

A second, separate flow. It turns typing into a pin, and never touches the planning flow above.

```mermaid
flowchart TD
    Screen["Map screen, as you type"] -->|"GET /api/v1/places/search, with where it is looking from"| SearchController[PlaceSearchController]
    SearchController --> PlaceSearch["PlaceSearch (interface)"]
    PlaceSearch --> Postgres[PostgresPlaceSearch]
    Postgres --> Address[("address: 2.4 million LINZ addresses")]
    Postgres --> Poi[("poi: 200,000 Overture places")]
    Linz["LINZ export"] -.->|AddressImporter| Address
    Overture["Overture export"] -.->|PoiImporter| Poi
    Address -.->|"suburb of the nearest address"| Poi
```

Dotted lines happen at import time, not on a search.

**Two datasets, each used for what it is the authority on.** LINZ publishes every New Zealand address but no names. Overture Maps publishes named places but is no address register. They meet in two places: one query searches both tables, and each place borrows its suburb from the nearest LINZ address, because Overture usually gives only the city.

**Found on everything, ranked on what people call it.** A place matches if the typed words appear anywhere in its searchable text. It is then scored on how well the words appear in its address, for an address, or in its name, for a named place. Scoring a business on its street address as well puts "McDonalds Road" ahead of McDonald's.

**A branch is as good a match as the brand.** 181 places have a name that starts with New World, and 27 of them are called just "New World". Measuring how alike the typing and the whole name are put those 27 first, from all over the country, and "New World Remuera" behind them for the word nobody typed. So the score asks a different question: do the words appear in the name together, as whole words? Every branch then scores the same, and something else is free to choose between them.

**Nearest first.** That something is distance. A search can say where it is looking from, and the score then falls gently the farther away a place is: to three quarters at 50 km, and never below half. Gently, because distance is there to choose between equal matches, not to promote a poor match for being close. The map screen sends where you are, if the browser shared that when the page opened. If it did not, it sends the start of the trip, and until there is a start, the middle of the map. The position is read once and not followed, and it is sent rounded to about 100 metres. One consequence: with your location shared, a trip planned for another town still gets results near you, until the town is typed as well.

**Both sides are simplified before they are compared.** A database function, `searchable`, drops accents and apostrophes. Each table stores the simplified text in a generated column with a trigram index, and the search runs what was typed through the same function. One address in eight has a macron, and a search that typed that word on an ordinary keyboard, without the macron, used to find nothing.

**The work for one search is bounded.** Each table hands over at most 5,000 matches to be ranked. "road" matches 850,000 addresses, and scoring them all took over four seconds. A search specific enough to be useful matches far fewer rows than the cap. When a search says where it is looking from, those 5,000 should be the nearest ones, so up to 20,000 matches are measured for distance first. Measuring is cheap. Scoring is what costs.

## Units and limits

| Thing | Decision |
| --- | --- |
| Coordinates | WGS84 decimal degrees, the numbers Google Maps shows |
| Distance | Kilometres. Rounded to two decimals in responses only, never during calculation |
| Duration | Seconds, from step 3 onwards |
| Stops per request | 10 at most. Checking every order grows by factorial, so 10 stops is already 3.6 million |
| Start | Required. Returning to it is optional |

## Two things real roads brought with them

**Distances stopped being symmetric.** A straight line from A to B equals the line back. A drive does not, because of one-way streets and motorway ramps, so every ordered pair is calculated separately rather than mirroring half the table.

**Some places cannot be reached at all.** A coordinate away from any street, on an airfield or inside a park, has nothing to snap to. That is not a broken request and not a broken server, so it answers 422 naming the place, rather than failing with a 500.

## Deliberately absent

Traffic data, because OpenStreetMap has none and live traffic costs money. Several drivers, time windows and vehicle capacity, until the single driver version is stable. Authentication, because there is nothing private to protect yet. Turn by turn navigation, which is handed to Google Maps or Waze.

## What each step changes

| Step | Change to this picture |
| --- | --- |
| 2 | **Search done.** LINZ addresses and Overture places in PostgreSQL behind a `PlaceSearch` interface, mirroring how distances work, with an endpoint the map screen calls. The planning flow above was untouched. Saved places still to come |
| 3 | **Done.** A second `TravelMatrixProvider` backed by GraphHopper, chosen by configuration. The service, the solver and the controller were not touched, which is what the seam was for |
| 4 | **Done.** A React app in `frontend/`, calling the same endpoint. The backend did not change. In development Vite proxies `/api` to port 8080, so there is no CORS setup |
| 5 | Vehicle details arrive in the request and reach the matrix provider |
| 6 | A second way to fill the cost matrix, using fuel instead of distance |
