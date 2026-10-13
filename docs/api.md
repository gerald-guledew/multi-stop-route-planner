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
- Some words are read both ways, whichever way they were typed. "12 bassett rd" finds 12 Bassett Road, "st heliers" finds Saint Heliers, and "80 saint lukes road" finds 80 St Lukes Road, which LINZ writes short. A full stop after one of them is ignored.

| Short | In full |
| --- | --- |
| `rd` | road |
| `st` | saint |
| `mt` | mount |
| `pt` | point |
| `ln` | lane |
| `tce` | terrace |
| `ct` | court |
| `pde` | parade |
| `hwy` | highway |
| `blvd` | boulevard |

Five more words find their abbreviation when they are typed in full: street, avenue, drive, place and crescent, written `st`, `ave`, `dr`, `pl` and `cres`. So "42 nuffield street" finds the shop that gives its address as "42 Nuffield St". Typed short these need no help. Each is the beginning of its word, and is found the way any unfinished word is.

### How results are ordered

Each match gets a score, and the highest comes first. The score is these multiplied together.

- **How well the words appear in what the place is called.** That is the address, for an address, and the name with or without its suburb, for a named place. Words that appear together, as whole words, score in full wherever in the name they are. So "new world" matches "New World Remuera" as fully as it matches "New World". The last word may be unfinished: "mcdonald" finds McDonald's.
- **Whether an address is the one typed.** An address that begins with the words keeps its whole score. One that only contains them keeps four fifths. So "12 queen street" lists number 12 before 112, "277 broadway" lists that address before the shops inside it, and "mcdonalds" lists the restaurants before the houses on McDonalds Road.
- **Whether a named place was found by its name.** One with a word in its name that begins with the first word typed keeps its whole score. One found only by its suburb or its street keeps four fifths. So "st heliers" lists what is named for Saint Heliers before what is merely there, and "mobil st heliers" still finds the Mobil in full.
- **How near it is, when `near` is given.** Next door keeps the whole score, 50 km away keeps three quarters, and nothing loses more than half. So distance chooses between equal matches, and a place whose name barely matches cannot jump ahead by being close. Distance counts in half kilometres, so the houses of one street stay in order.

Between equal scores, the name the words account for most of comes first, then the place the data is more sure of. Without `near` most matches score the same, and this is what orders them.

### Known gaps

- A word typed in full is read as its abbreviation only beside another word of three letters or more. "saint he" finds what is written Saint. From "saint hel" on, what is written St Heliers is found too.
- Other abbreviations are not read. "ter" finds Terrace, being the beginning of the word, but "terrace" does not find a place that writes "Ter".
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
| `legs[].climbMetres` | integer | How much that leg goes up, counting rises of 10 m or more. `null` unless the roads were read with their heights. See "Climb and descent" below |
| `legs[].descentMetres` | integer | How much that leg comes down, as a number above zero. `null` whenever the climb is |
| `totalDistanceKm` | number | Distance of the whole trip in the returned order |
| `totalDurationSeconds` | integer | Driving time for the whole trip. `null` on straight lines |
| `totalClimbMetres` | integer | Climb of the whole trip. `null` unless the roads were read with their heights |
| `totalDescentMetres` | integer | Descent of the whole trip. `null` whenever the climb is |
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
    { "from": "Sky Tower",   "to": "Takapuna",    "distanceKm": 6.92,  "durationSeconds": null, "climbMetres": null, "descentMetres": null },
    { "from": "Takapuna",    "to": "Devonport",   "distanceKm": 5.46,  "durationSeconds": null, "climbMetres": null, "descentMetres": null },
    { "from": "Devonport",   "to": "Sylvia Park", "distanceKm": 10.19, "durationSeconds": null, "climbMetres": null, "descentMetres": null },
    { "from": "Sylvia Park", "to": "Airport",     "distanceKm": 11.31, "durationSeconds": null, "climbMetres": null, "descentMetres": null },
    { "from": "Airport",     "to": "Sky Tower",   "distanceKm": 17.87, "durationSeconds": null, "climbMetres": null, "descentMetres": null }
  ],
  "totalDistanceKm": 51.76,
  "totalDurationSeconds": null,
  "totalClimbMetres": null,
  "totalDescentMetres": null,
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

### Climb and descent

The map says where a road runs and not how high it is. So a plan says nothing about hills unless the roads were read together with the height of the ground, which is a setting of its own:

```yaml
routeplanner:
  routing:
    provider: graphhopper
    heights:
      enabled: true
```

The first start with this on fetches the height of the ground for every part of the map that has a road in it, which for New Zealand is 70 files and 480 MB, and prepares a second graph beside the first. That took a minute and a half where it was measured. Later starts fetch nothing. The files go to `backend/data/heights` and the graph to `backend/data/graph-cache-heights`, both out of git.

Each leg then says how much it goes up and how much it comes down, and the plan says the same for the whole trip, all in whole metres. By road the five stops above climb 478 m and descend 478 m:

```json
{ "from": "Sky Tower", "to": "Airport", "distanceKm": 20.29, "durationSeconds": 1261, "climbMetres": 92, "descentMetres": 132 }
```

What these figures are, and are not.

- **Both are added along the road.** Neither is the difference in height between two places. The leg above ends 40 m lower than it began, and climbs 92 m on the way.
- **The descent is a number above zero**, like the climb. The climb less the descent is how much higher the leg ends than it began.
- **On a round trip the two totals come out equal**, give or take a metre of rounding, because the trip ends where it began.
- **A level leg is `0` and `0`.** That is an answer. With heights off, and on straight lines, all four fields are `null`, which means not known.
- **The order is still chosen for distance.** The climb is what the shortest order climbs. Another order can climb less and be longer.

#### How the climb is counted

The heights are read off a grid of the ground, a whole number of metres every 30 m or so. The ground is not the road. A road cut into a hillside is read partly off the slope above it and partly off the slope below. Added up as they stand, the heights along a level road across the Hauraki Plains came to 113 m of climbing in 19 km.

So two things are done before anything is counted:

- **Each height is evened out with the 150 m of road around it.** One wrong height then moves the road very little.
- **A rise or a dip counts only when the road turns by 10 m or more.** Anything smaller is taken for a wobble in the grid. A real rise of 8 m is lost with the wobbles, which is the price.

Both settings were chosen by measuring. A road that only climbs has to climb at least the height it gains, and whatever is counted beyond that is error or a dip too small to see on a map:

| Road | Height gained | Counted | Added up as the heights stand |
| --- | --- | --- | --- |
| Crown Range, from the river to the summit | 709 m | 722 m | 809 m |
| Remutaka Hill, from Featherston to the summit | 513 m | 523 m | 771 m |
| Tākaka Hill, from the valley floor to the top | 815 m | 856 m | 1,213 m |
| Arthur's Pass, from Otira to the pass | 531 m | 591 m | 813 m |
| Hauraki Plains, 19 km of level road | 0 m | 0 m | 113 m |

The summits themselves read 3 to 8 m above their published heights on six passes out of seven. Tākaka Hill reads 26 m above.

Take the figure as an estimate. Three things it is known to get wrong:

- **A bridge is drawn as one even slope from end to end**, because the ground under it is the water. The Auckland Harbour Bridge rises more than 40 m and is counted as 4.
- **In a gorge it runs high.** Where the road is cut into a steep side, even 150 m of evening out leaves some of the hillside in. Arthur's Pass is 11% over.
- **Under a cliff it reads some of the cliff.** A level waterfront road in Auckland still comes to 37 m in 8 km.

The figure for a city leg moves by a third or so if 10 m is changed to 5 m or to 15 m. The rule is in `HeightProfile`, with the two settings at the top.

#### What heights do not change

- **The distance.** It is measured on the map, with heights or without. The routing engine itself measures a road with heights along its slope, bumps of the grid included, which made the road through the Otira Gorge 1.3% longer than it measures on the map.
- **The order of the stops**, since that is chosen for distance.

The driving time does move a little, because the engine times each road along its slope: 5,063 seconds for the five stops against 5,057.

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
