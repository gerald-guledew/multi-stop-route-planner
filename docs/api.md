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
| `totalDistanceKm` | number | Distance of the whole trip in the returned order |
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
    { "from": "Sky Tower",   "to": "Takapuna",    "distanceKm": 6.92 },
    { "from": "Takapuna",    "to": "Devonport",   "distanceKm": 5.46 },
    { "from": "Devonport",   "to": "Sylvia Park", "distanceKm": 10.19 },
    { "from": "Sylvia Park", "to": "Airport",     "distanceKm": 11.31 },
    { "from": "Airport",     "to": "Sky Tower",   "distanceKm": 17.87 }
  ],
  "totalDistanceKm": 51.76,
  "enteredOrderDistanceKm": 71.61,
  "ordersChecked": 24
}
```

Two things that look like bugs and are not. The legs above add up to 51.75 rather than 51.76, because totals are calculated before rounding. And on a round trip the reverse order is exactly as short, so either direction is a correct answer.

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
