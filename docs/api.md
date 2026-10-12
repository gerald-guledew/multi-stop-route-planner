# API

Base path `/api/v1`. JSON in, JSON out.

**Status: working.** If a field gets a different name while building, change it here in the same pull request.

### Straight lines or real roads

The same request answers differently depending on which distance source is switched on in `application.yaml`:

```yaml
routeplanner:
  routing:
    provider: haversine     # straight lines, no setup
    # provider: graphhopper # real driving distances, needs the map extract
```

`graphhopper` needs the New Zealand extract, 385 MB, at `backend/data/new-zealand-latest.osm.pbf`:

```bash
curl -o backend/data/new-zealand-latest.osm.pbf https://download.geofabrik.de/australia-oceania/new-zealand-latest.osm.pbf
```

The first start builds a routing graph and takes minutes. Later starts load it in seconds. The map file and the graph stay out of git.

The difference is not cosmetic. For the five Auckland stops below, straight lines give a 51.57 km trip and real roads give 76.91 km, **in a different order**, because the harbour is only short if you can swim.

## GET /api/v1/places/search

Finds places by what you type, so a stop can be typed rather than hunted for on the map. A place is either a street address or a named place: a shop, a school, a hospital.

| Parameter | Rules |
| --- | --- |
| `q` | Required. Searches of fewer than three characters return an empty list rather than half the country |
| `near` | Optional. Where the search is looking from, as `latitude,longitude`. The nearest of several matches then comes first |
| `limit` | Optional, 1 to 25, default 8 |

```
GET /api/v1/places/search?q=new%20world%20remuera
```

```json
[
  {
    "kind": "poi",
    "name": "New World Remuera",
    "detail": "10 Clonbern Rd, Remuera, Auckland",
    "latitude": -36.88169017,
    "longitude": 174.79746689
  }
]
```

```
GET /api/v1/places/search?q=90a%20bassett%20road
```

```json
[
  {
    "kind": "address",
    "name": "90A Bassett Road, Remuera, Auckland",
    "detail": null,
    "latitude": -36.8689416667,
    "longitude": 174.7832303333
  }
]
```

With `near`, the nearest of several matches comes first. Without it the same search lists branches from all over the country.

```
GET /api/v1/places/search?q=new%20world&near=-36.871,174.787&limit=2
```

```json
[
  {
    "kind": "poi",
    "name": "New World Newmarket",
    "detail": "42 Nuffield St, Newmarket, Auckland",
    "latitude": -36.87204269,
    "longitude": 174.7780286
  },
  {
    "kind": "poi",
    "name": "New World Remuera",
    "detail": "10 Clonbern Rd, Remuera, Auckland",
    "latitude": -36.88169017,
    "longitude": 174.79746689
  }
]
```

`near` is whatever point the caller wants results close to. The map screen sends where you are, if the browser shared that when the page opened. If it did not, it sends the start of the trip, or the middle of the map while there is no start. Three decimal places, about 100 metres, is plenty, because the ranking counts distance in half kilometres. A `near` that is not two numbers in range is a 400.

| Field | Meaning |
| --- | --- |
| `kind` | `address`, or `poi` for a named place |
| `name` | The full address, or the name of the place |
| `detail` | Where a named place is, to tell two with the same name apart. `null` for an address |
| `latitude`, `longitude` | Where to drop the pin |

### How typing is matched

- Each space becomes a wildcard, so "bassett road remuera" matches "20A Bassett Road, Remuera, Auckland" despite the comma. Words have to be typed in the order they appear. A comma counts as a space.
- Accents and apostrophes are ignored on both sides. "whangarei" finds Whangārei, and "mcdonalds" finds McDonald's.
- A named place is found by its name, its address, its suburb or its city. "the warehouse newmarket" works although the name is only "The Warehouse".

### How results are ordered

Each match gets a score, and the highest comes first. The score is three things multiplied together.

- **How well the words appear in what the place is called.** That is the address, for an address, and the name with or without its suburb, for a named place. Words that appear together, as whole words, score in full wherever in the name they are. So "new world" matches "New World Remuera" as fully as it matches "New World". The last word may be unfinished: "mcdonald" finds McDonald's.
- **Whether an address is the one typed.** An address that begins with the words keeps its whole score. One that only contains them keeps four fifths. So "12 queen street" lists number 12 before 112, "277 broadway" lists that address before the shops inside it, and "mcdonalds" lists the restaurants before the houses on McDonalds Road.
- **How near it is, when `near` is given.** Next door keeps the whole score, 50 km away keeps three quarters, and nothing loses more than half. So distance chooses between equal matches, and a place whose name barely matches cannot jump ahead by being close. Distance counts in half kilometres, so the houses of one street stay in order.

Between equal scores, the name the words account for most of comes first, then the place the data is more sure of. Without `near` most matches score the same, and this is what orders them.

### Known gaps

- Abbreviations are not expanded. "12 bassett rd" does not find 12 Bassett Road, and "mt eden" does not find an address in Mount Eden.
- A unit number LINZ does not hold finds nothing. "3/90A Bassett Road" fails where "90A Bassett Road" works.
- Near means in a straight line, not by road. Across a harbour, the nearest branch to drive to can be a different one.
- Words are matched as a run. "auckland hospital" lists "Auckland Hospital Foundation" before "Auckland City Hospital", because "City" interrupts the run. Typing the name in full finds it.
- A word that matches more than 20,000 rows of a table, such as "road" or "auckland" alone, is ranked from the first 20,000 found, not the nearest. A second word fixes it.

Named places come from Overture Maps, which scores each one from 0 to 1 for how likely it is to exist. Those under `routeplanner.search.min-confidence`, 0.3 by default, are left out.

Results are empty until the data is imported: LINZ addresses for `address`, Overture places for `poi`. See the README.

## POST /api/v1/routes/optimize

Takes a starting point and a list of places. Returns the order that makes the trip shortest, with the distance of every leg.

Distances are straight lines for now. Step 3 replaces them with real driving distances.

### Request

| Field | Type | Rules |
| --- | --- | --- |
| `start` | object | Required |
| `start.name` | string | Required, 1 to 100 characters |
| `start.latitude` | number | Required, between -90 and 90 |
| `start.longitude` | number | Required, between -180 and 180 |
| `stops` | array | Required, 1 to 10 items, each shaped like `start` |
| `stops[].keepInPlace` | boolean | Optional, defaults to `false`. See "Keeping a stop in its place" below |
| `returnToStart` | boolean | Optional, defaults to `true` |

```json
{
  "start": { "name": "Sky Tower", "latitude": -36.8485, "longitude": 174.7621 },
  "stops": [
    { "name": "Takapuna",    "latitude": -36.7870, "longitude": 174.7740 },
    { "name": "Airport",     "latitude": -37.0082, "longitude": 174.7850 },
    { "name": "Devonport",   "latitude": -36.8330, "longitude": 174.7955 },
    { "name": "Sylvia Park", "latitude": -36.9170, "longitude": 174.8414 }
  ],
  "returnToStart": true
}
```

### Response, 200

| Field | Type | Meaning |
| --- | --- | --- |
| `route` | array | The places in the order to drive them, starting at `start`, ending back there when `returnToStart` is true |
| `legs` | array | One entry per drive between two consecutive places |
| `legs[].from`, `legs[].to` | string | Place names |
| `legs[].distanceKm` | number | Distance for that leg |
| `legs[].durationSeconds` | integer | Driving time for that leg. `null` on straight lines. See "Driving times" below |
| `totalDistanceKm` | number | Distance of the whole trip in the returned order |
| `totalDurationSeconds` | integer | Driving time for the whole trip. `null` on straight lines |
| `enteredOrderDistanceKm` | number | Distance if the stops were driven in the order they were sent, so the saving is visible |
| `ordersChecked` | integer | How many orders were compared |

```json
{
  "route": [
    { "name": "Sky Tower",   "latitude": -36.8485, "longitude": 174.7621 },
    { "name": "Takapuna",    "latitude": -36.7870, "longitude": 174.7740 },
    { "name": "Devonport",   "latitude": -36.8330, "longitude": 174.7955 },
    { "name": "Sylvia Park", "latitude": -36.9170, "longitude": 174.8414 },
    { "name": "Airport",     "latitude": -37.0082, "longitude": 174.7850 },
    { "name": "Sky Tower",   "latitude": -36.8485, "longitude": 174.7621 }
  ],
  "legs": [
    { "from": "Sky Tower",   "to": "Takapuna",    "distanceKm": 6.92,  "durationSeconds": null },
    { "from": "Takapuna",    "to": "Devonport",   "distanceKm": 5.46,  "durationSeconds": null },
    { "from": "Devonport",   "to": "Sylvia Park", "distanceKm": 10.19, "durationSeconds": null },
    { "from": "Sylvia Park", "to": "Airport",     "distanceKm": 11.31, "durationSeconds": null },
    { "from": "Airport",     "to": "Sky Tower",   "distanceKm": 17.87, "durationSeconds": null }
  ],
  "totalDistanceKm": 51.76,
  "totalDurationSeconds": null,
  "enteredOrderDistanceKm": 71.61,
  "ordersChecked": 24
}
```

Two things that look like bugs and are not. The legs above add up to 51.75 rather than 51.76, because totals are calculated before rounding. And on a round trip the reverse order is exactly as short, so either direction is a correct answer.

### Driving times

On the `graphhopper` provider each leg also says how long the drive takes, and the plan says how long the whole trip takes. By road the five stops above come to 76.91 km and 5,057 seconds, a little over 84 minutes:

```json
{ "from": "Sky Tower", "to": "Airport", "distanceKm": 20.29, "durationSeconds": 1260 }
```

Three things to know about these figures.

- **They are for clear roads.** The time is what each road's speed allows with nothing in the way. OpenStreetMap carries no traffic, so the same drive at five in the afternoon takes longer.
- **They are driving time alone.** Nothing is added for the time spent at a stop.
- **The order is still chosen for distance.** The time is how long the shortest order takes. Another order can be quicker and longer.

On straight lines both fields are `null`, not zero. A straight line says how far apart two places are, and nothing about how fast anybody gets from one to the other.

### Keeping a stop in its place

Some stops cannot go wherever is shortest. The first call of the day that cannot wait, or the last one before heading home. Mark such a stop with `keepInPlace` and it is visited at the turn it was sent in: sent first, visited first. The other stops are arranged around it.

Take the request above and keep the Airport, which was sent second, in its place:

```json
{ "name": "Airport", "latitude": -37.0082, "longitude": 174.7850, "keepInPlace": true }
```

The route becomes Sky Tower, Sylvia Park, **Airport**, Devonport, Takapuna and back to Sky Tower. That is 53.58 km, where 51.76 km was possible with every stop free. `ordersChecked` falls from 24 to 6, because only three stops are left to arrange.

- Keeping a stop can only cost distance or leave it the same. It never saves any.
- With every stop kept there is one order, the one sent, and the answer is that trip.
- On `start` the mark is ignored. The start is first anyway.

### Errors

Validation failures return **400** as RFC 9457 Problem Details, with the media type `application/problem+json`.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/v1/routes/optimize",
  "errors": [
    { "field": "stops[1].latitude", "message": "must be between -90 and 90" },
    { "field": "start.name", "message": "must not be blank" }
  ]
}
```

| Status | When |
| --- | --- |
| 400 | A field is missing, a coordinate is out of range, there are no stops, or there are more than 10 |
| 422 | A place has no road near it, so no car can reach it. Only possible on the `graphhopper` provider |
| 500 | Anything unexpected. The response carries no internal details |

A 422 names the place that cannot be reached, so the caller knows which pin to move:

```json
{
  "type": "about:blank",
  "title": "Unroutable place",
  "status": 422,
  "detail": "Cannot route to Airport. No road was found near those coordinates. Move the point closer to a street.",
  "instance": "/api/v1/routes/optimize",
  "place": "Airport"
}
```

This is easy to hit in practice. A pin dropped on an airfield, inside a shopping centre or in the middle of a park has no street beside it. Moving it to the nearest road fixes it: the Auckland Airport pin at `-37.0082, 174.7850` fails, while the terminal forecourt at `-37.0070, 174.7830` works.

## Saved places

Places somebody named so they can pick them again without typing: home, the office, a shop the map data does not know about. They live in the database, so they are still there after a restart.

There is one list. The application has no users, so whoever can reach it can read the list, and the list may well hold a home address. What keeps it private is in "One app" in [architecture.md](architecture.md).

### GET /api/v1/saved-places

Every saved place, in name order. Capitals and macrons do not change the order.

```json
[
  { "id": 7, "name": "Home", "latitude": -36.8689416667, "longitude": 174.7832303333 },
  { "id": 9, "name": "The office", "latitude": -36.8485, "longitude": 174.7621 }
]
```

The answer is marked `Cache-Control: no-store`, so neither the browser nor anything in between keeps a copy.

### POST /api/v1/saved-places

| Field | Type | Rules |
| --- | --- | --- |
| `name` | string | Required, 1 to 100 characters. Spaces around it are dropped |
| `latitude` | number | Required, between -90 and 90 |
| `longitude` | number | Required, between -180 and 180 |

```json
{ "name": "Home", "latitude": -36.8689416667, "longitude": 174.7832303333 }
```

Answers **201** with the place as saved. The `id` is what to remove it by.

```json
{ "id": 7, "name": "Home", "latitude": -36.8689416667, "longitude": 174.7832303333 }
```

A name can be saved once, whatever the capitals. Saving "HOME" while "Home" is there answers **409**, and the place that was there stays as it was:

```json
{
  "type": "about:blank",
  "title": "Name already saved",
  "status": 409,
  "detail": "A place called \"HOME\" is already saved. Remove it first, or save this one under another name.",
  "instance": "/api/v1/saved-places",
  "name": "HOME"
}
```

One spot may be saved under two names.

### DELETE /api/v1/saved-places/{id}

Answers **204** with no body. The name is then free to be saved again.

### Errors

| Status | When |
| --- | --- |
| 400 | A field is missing or out of range, or the id is not a number |
| 404 | Removing a place that is not there |
| 409 | Saving under a name that is taken |

All three are Problem Details, as above.

Planning takes positions, not ids. To plan a trip through a saved place, send its name and position like any other place.
