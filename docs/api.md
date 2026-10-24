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
GET /api/v1/places/search?q=286%20mount%20wellington%20highway&limit=1
```

```json
[
  {
    "kind": "address",
    "name": "286 Mount Wellington Highway, Mount Wellington, Auckland",
    "detail": null,
    "latitude": -36.9145359,
    "longitude": 174.8391820167
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

An address can be typed with a flat in front of it. LINZ writes a flat before a slash, as in "M/205 Queen Street", and lists the flats of some buildings and not of others. So when an address with a flat finds nothing, it is looked for again without the flat: "7/205 queen street" finds 205 Queen Street. A flat typed in words, "Unit 7, 205 Queen Street", is first looked for as LINZ writes it and then without it. The words read this way are unit, flat, apartment, apt, shop, suite and villa. None of this happens while the words as typed find something.

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
- Near means in a straight line, not by road. Across a harbour, the nearest branch to drive to can be a different one.
- Words are matched as a run. "auckland hospital" lists "Auckland Hospital Foundation" before "Auckland City Hospital", because "City" interrupts the run. Typing the name in full finds it.
- A word that matches more than 20,000 rows of a table, such as "road" or "auckland" alone, is ranked from the first 20,000 found, not the nearest. A second word fixes it.

Named places come from Overture Maps, which scores each one from 0 to 1 for how likely it is to exist. Those under `routeplanner.search.min-confidence`, 0.3 by default, are left out.

Results are empty until the data is imported: LINZ addresses for `address`, Overture places for `poi`. See the README.

## POST /api/v1/routes/optimize

Takes a starting point and a list of places. Returns the order that makes the trip shortest, with the distance of every leg. Asked to factor in road climbs, it returns the order, and the roads between the stops, that come out best with the climb counted as well. Asked for the fastest order, it returns the order with the least driving time. Asked for the least fuel, it returns the order that takes the least fuel in the vehicle sent.

Distances are straight lines unless real roads are switched on, as under "Straight lines or real roads" above.

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
| `factorInRoadClimbs` | boolean | Optional, defaults to `false`. See "Factoring in road climbs" below |
| `fastest` | boolean | Optional, defaults to `false`. Not together with `factorInRoadClimbs`. See "The fastest order" below |
| `leastFuel` | boolean | Optional, defaults to `false`. Needs `vehicle`. Not together with `fastest` or `factorInRoadClimbs`. See "The least fuel" below |
| `vehicle` | object | Optional. The vehicle to work out the fuel for. See "Fuel" below |
| `vehicle.litresPer100Km` | number | Required with `vehicle`, 2 to 30. What its dashboard shows, or the register's figure for a car found there |
| `vehicle.kilograms` | number | Optional, 400 to 4,000. What the vehicle weighs empty. Without it the fuel does not count the hills |

```json
{
  "start": { "name": "Sky Tower", "latitude": -36.8485, "longitude": 174.7621 },
  "stops": [
    { "name": "Takapuna",    "latitude": -36.7870, "longitude": 174.7740 },
    { "name": "Airport",     "latitude": -37.0070, "longitude": 174.7830 },
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
| `legs[].fuelLitres` | number | The fuel that leg takes. `null` when no `vehicle` was sent |
| `legs[].path` | array | The road that leg follows, as `[latitude, longitude]` pairs, for drawing it. Empty on straight lines |
| `legs[].turns` | array | The turns along that leg's road, from setting off to arriving. Empty on straight lines. See "Turns" below |
| `totalDistanceKm` | number | Distance of the whole trip in the returned order |
| `totalDurationSeconds` | integer | Driving time for the whole trip. `null` on straight lines |
| `totalClimbMetres` | integer | Climb of the whole trip. `null` unless the roads were read with their heights |
| `totalDescentMetres` | integer | Descent of the whole trip. `null` whenever the climb is |
| `totalFuelLitres` | number | The fuel the whole trip takes. `null` when no `vehicle` was sent |
| `fuelCountsHills` | boolean | Whether the fuel counts the hills. `false` when the roads were read without heights or the `vehicle` came without `kilograms`. `null` when no `vehicle` was sent |
| `enteredOrderDistanceKm` | number | Distance if the stops were driven in the order they were sent, so the saving is visible |
| `shortestOrder` | object | Only when road climbs were factored in, or the fastest order or the least fuel was asked for: the `distanceKm`, `durationSeconds`, `climbMetres`, `descentMetres` and `fuelLitres` of the shortest order, to hold this one against. Its time is `null` on straight lines, its climb and descent without heights, and its fuel without a `vehicle`, as for the plan. `null` otherwise |
| `ordersChecked` | integer | How many orders were compared |

```json
{
  "route": [
    { "name": "Sky Tower",   "latitude": -36.8485, "longitude": 174.7621 },
    { "name": "Takapuna",    "latitude": -36.7870, "longitude": 174.7740 },
    { "name": "Devonport",   "latitude": -36.8330, "longitude": 174.7955 },
    { "name": "Sylvia Park", "latitude": -36.9170, "longitude": 174.8414 },
    { "name": "Airport",     "latitude": -37.0070, "longitude": 174.7830 },
    { "name": "Sky Tower",   "latitude": -36.8485, "longitude": 174.7621 }
  ],
  "legs": [
    { "from": "Sky Tower",   "to": "Takapuna",    "distanceKm": 6.92,  "durationSeconds": null, "climbMetres": null, "descentMetres": null, "fuelLitres": null, "path": [], "turns": [] },
    { "from": "Takapuna",    "to": "Devonport",   "distanceKm": 5.46,  "durationSeconds": null, "climbMetres": null, "descentMetres": null, "fuelLitres": null, "path": [], "turns": [] },
    { "from": "Devonport",   "to": "Sylvia Park", "distanceKm": 10.19, "durationSeconds": null, "climbMetres": null, "descentMetres": null, "fuelLitres": null, "path": [], "turns": [] },
    { "from": "Sylvia Park", "to": "Airport",     "distanceKm": 11.27, "durationSeconds": null, "climbMetres": null, "descentMetres": null, "fuelLitres": null, "path": [], "turns": [] },
    { "from": "Airport",     "to": "Sky Tower",   "distanceKm": 17.72, "durationSeconds": null, "climbMetres": null, "descentMetres": null, "fuelLitres": null, "path": [], "turns": [] }
  ],
  "totalDistanceKm": 51.57,
  "totalDurationSeconds": null,
  "totalClimbMetres": null,
  "totalDescentMetres": null,
  "totalFuelLitres": null,
  "fuelCountsHills": null,
  "enteredOrderDistanceKm": 71.35,
  "shortestOrder": null,
  "ordersChecked": 24
}
```

Two things that look like bugs and are not. The legs above add up to 51.56 rather than 51.57, because totals are calculated before rounding. And on a round trip the reverse order is exactly as short, so either direction is a correct answer.

### Driving times

On the `graphhopper` provider each leg also says how long the drive takes, and the plan says how long the whole trip takes. By road the five stops above come to 76.91 km and 5,057 seconds, a little over 84 minutes:

```json
{ "from": "Sky Tower", "to": "Airport", "distanceKm": 20.29, "durationSeconds": 1260 }
```

Three things to know about these figures.

- **They are for clear roads.** The time is what each road's speed allows with nothing in the way. OpenStreetMap carries no traffic, so the same drive at five in the afternoon takes longer.
- **They are driving time alone.** Nothing is added for the time spent at a stop.
- **The order is chosen for distance unless the fastest is asked for.** The time is then how long the shortest order takes, and another order can be quicker and longer. See "The fastest order" below.

On straight lines both fields are `null`, not zero. A straight line says how far apart two places are, and nothing about how fast anybody gets from one to the other.

### Turns

On the `graphhopper` provider each leg also lists its turns along the road drawn for it, from setting off to arriving. From Sylvia Park to Devonport Wharf, the first five of twelve:

```json
"turns": [
  { "turn": "setOff", "street": null, "follows": null, "towards": null, "motorwayExit": null, "roundaboutExit": null, "heading": 347, "distanceMetres": 99 },
  { "turn": "left", "street": "Te Putu Avenue", "follows": null, "towards": null, "motorwayExit": null, "roundaboutExit": null, "heading": null, "distanceMetres": 68 },
  { "turn": "roundabout", "street": "Te Putu Avenue", "follows": null, "towards": null, "motorwayExit": null, "roundaboutExit": 2, "heading": null, "distanceMetres": 52 },
  { "turn": "keepLeft", "street": null, "follows": "Mount Wellington Highway", "towards": null, "motorwayExit": null, "roundaboutExit": null, "heading": null, "distanceMetres": 363 },
  { "turn": "sharpRight", "street": null, "follows": "Auckland Southern Motorway", "towards": null, "motorwayExit": null, "roundaboutExit": null, "heading": null, "distanceMetres": 13431 }
]
```

| Field | Meaning |
| --- | --- |
| `turn` | Which way: `setOff`, `continue`, `slightLeft`, `left`, `sharpLeft`, `slightRight`, `right`, `sharpRight`, `keepLeft`, `keepRight`, `uTurn`, `roundabout`, `leaveRoundabout`, `ferry` or `arrive` |
| `street` | The road it goes onto, by its name, or by its number when the map gives it none. `null` when the map gives neither |
| `follows` | When `street` is `null`: the road the drive after the turn mostly follows. `null` otherwise |
| `towards` | Where the signs point. `null` where none do |
| `motorwayExit` | The name of the motorway exit it takes. `null` for any other turn |
| `roundaboutExit` | Which exit to take, counting from where the roundabout is entered. `null` for any other turn |
| `heading` | The bearing it sets off on, in whole degrees clockwise from north. `null` for any turn but `setOff` |
| `distanceMetres` | How far to drive after it, before the next turn. Nought on arriving |

The answer gives what each turn is and leaves the words to the screen. It says these "Head north", "Turn left onto Te Putu Avenue", "At the roundabout, take the 2nd exit onto Te Putu Avenue", "Keep left, then follow Mount Wellington Highway" and "Turn sharp right, then follow Auckland Southern Motorway".

Things to know.

- **They come from OpenStreetMap**, through the routing engine: the names and numbers of the roads, where the signs point, and the names of motorway exits. On the 400 round trips measured for the fastest order, 95.4% of the 2,053 street names in the turns are roads with LINZ addresses. Most of the rest are motorways, road numbers, lanes and bridges with no addresses, or names written another way, such as "Mount Saint John Avenue" for "Mount St John Avenue".
- **A turn onto a road the map does not name names the road after it.** That is an on-ramp most often, and `follows` names the motorway it leads to. On those trips the share of the driving that came after a turn naming no road fell from 10.4% to 0.5%.
- **Where the map names nothing, a turn says only which way.** 11% of turns on those trips did, a driveway or a slip lane mostly.
- **They add up to the leg, near enough.** With heights on, the engine measures each stretch along its slope, and the leg is measured on the map. On legs of 5 km or more the two were within 0.5%. From Featherston up the Remutaka hill the turns come to 0.9% more.
- **They follow the roads drawn on the map**, which the plan's distance and time are for. The Google Maps link carries the stops only, so Google's own directions can take other roads.
- **On straight lines `turns` is empty.**

### Climb and descent

The map says where a road runs and not how high it is. So a plan says nothing about hills unless the roads were read together with the height of the ground, which is a setting of its own:

```yaml
routeplanner:
  routing:
    provider: graphhopper
    heights:
      enabled: true
```

The first start with this on fetches the height of the ground for every part of the map that has a road in it, which for New Zealand is 70 files and 480 MB, and prepares a second graph beside the first. That took a minute and a half where it was measured. Later starts fetch nothing. The files go to `backend/data/heights` and the graph to `backend/data/graph-cache-heights-2`, both out of git. The graph also holds the roads that climb less, see "Factoring in road climbs" below. A graph in `backend/data/graph-cache-heights`, prepared before there were any, is no longer read and can be deleted.

Each leg then says how much it goes up and how much it comes down, and the plan says the same for the whole trip, all in whole metres. By road the five stops above climb 478 m and descend 478 m:

```json
{ "from": "Sky Tower", "to": "Airport", "distanceKm": 20.29, "durationSeconds": 1261, "climbMetres": 92, "descentMetres": 132 }
```

What these figures are, and are not.

- **Both are added along the road.** Neither is the difference in height between two places. The leg above ends 40 m lower than it began, and climbs 92 m on the way.
- **The descent is a number above zero**, like the climb. The climb less the descent is how much higher the leg ends than it began.
- **On a round trip the two totals come out equal**, give or take a metre of rounding, because the trip ends where it began.
- **A level leg is `0` and `0`.** That is an answer. With heights off, and on straight lines, all four fields are `null`, which means not known.
- **The order is chosen for distance unless asked otherwise.** The climb is then what the shortest order climbs on the usual roads, and another order or other roads can climb less and be longer. See "Factoring in road climbs" below.

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
- **The order of the stops and the roads between them**, unless the request asks to factor in road climbs. Left alone the order is chosen for distance, and the roads are the routing engine's usual ones.

The driving time does move a little, because the engine times each road along its slope: 5,063 seconds for the five stops against 5,057.

### Factoring in road climbs

Send `"factorInRoadClimbs": true` and the order of the stops and the roads between them are chosen for distance and climb together. Left out, the order is chosen for distance alone, along the usual roads. Every metre a leg climbs then counts as 10 m of road on top of its length, so an order that is 1 km longer comes out ahead when it climbs more than 100 m less.

The roads between two stops are chosen in two steps:

- **The routing engine finds the roads that climb less.** It weighs each stretch of road as it usually does, and then as if it were 10 m longer for every metre the stretch climbs. A road round a hill can then come out ahead of the road over it.
- **The planner keeps them where they are worth it.** The engine counts every rise of the ground along a road, wobbles of the height grid included, where the planner evens the heights out first. So by the planner's count the engine's roads can come out longer and climbing more. A leg takes them only when they climb at least 10 m less than the usual roads and come to less, with the climb counted as road. Otherwise it keeps the usual roads.

The order is then chosen over the roads each leg would take, so the order and the roads are chosen together.

The answer also says what the shortest order comes to, in `shortestOrder`, so that what was given up for what can be seen. Four stops from Avondale and back, by road with heights:

```json
{
  "start": { "name": "Avondale", "latitude": -36.8990, "longitude": 174.6990 },
  "stops": [
    { "name": "Glen Eden",      "latitude": -36.9110, "longitude": 174.6500 },
    { "name": "Blockhouse Bay", "latitude": -36.9200, "longitude": 174.7010 },
    { "name": "Takapuna",       "latitude": -36.7870, "longitude": 174.7740 },
    { "name": "Mission Bay",    "latitude": -36.8490, "longitude": 174.8310 }
  ],
  "factorInRoadClimbs": true
}
```

| | Order of the stops | Distance | Climb |
| --- | --- | --- | --- |
| Shortest | Glen Eden, Blockhouse Bay, Mission Bay, Takapuna | 62.58 km | 666 m |
| The order below, on the usual roads | Glen Eden, Blockhouse Bay, Takapuna, Mission Bay | 62.88 km | 561 m |
| With road climbs factored in | Glen Eden, Blockhouse Bay, Takapuna, Mission Bay | 62.92 km | 524 m |

Two legs take other roads. From Takapuna to Mission Bay the road keeps to Quay Street along the waterfront instead of Customs Street a block inland, and climbs 59 m instead of 78 m for 0.03 km less.

```json
{
  "totalDistanceKm": 62.92,
  "totalClimbMetres": 524,
  "totalDescentMetres": 524,
  "shortestOrder": { "distanceKm": 62.58, "durationSeconds": 4728, "climbMetres": 666, "descentMetres": 666, "fuelLitres": null }
}
```

Things to know.

- **Often the shortest order stands, on the usual roads.** `shortestOrder` then repeats the plan's own figures. The five stops further up are one such trip.
- **What it came to on 400 made-up round trips**, the ones measured for the fastest order below: four to eight stops around Auckland, 85 km and 659 m of climb at the middle. The order changed in 82 and the roads alone in another 132. Where either did, the trip climbed 30 m less at the middle of the range, 5% of its climb, and up to 155 m. It was 0.06 km longer at the middle, and from 1.42 km shorter to 0.68 km longer. Over all 400 the climb came down 3.0% and the distance did not move. When the order alone was chosen for climb, the climb came down 1.0%.
- **The driving time can go up**, since a road that climbs less can be slower. 7 seconds at the middle of the trips that changed. Of the 400, 28 took a minute or more longer, 4 of them five minutes or more, and 21 took a minute or more less.
- **A shorter route can climb a little more.** Roads that climb less on one leg can open a shorter order that climbs more on another. Two of the 400 came out 0.75 km and 0.96 km shorter than the shortest order, and climbed 3 m and 5 m more.
- **Without heights there is no climb to count.** On straight lines, and on roads read without their heights, the order is chosen for distance as if nothing had been asked, and `shortestOrder` is `null`. It is not an error.
- **Coming down counts for nothing.** A car gets some of a climb back on the way down. That is left out.
- **`totalDistanceKm` is the distance driven.** The road that the climb was counted as is used to choose the order and the roads, and appears in no figure.
- **`enteredOrderDistanceKm` is the order sent, along the same roads as the plan**, so the two orders are compared on the same roads.
- **Google Maps picks its own roads.** The link keeps the order of the stops and lets Google choose the roads between them, so the roads that climb less are the ones on this map, and not those in Google's directions.
- **The trip can be longer than the order sent**, so `enteredOrderDistanceKm` can be the smaller of the two.
- **A stop kept in its place stays there.**
- **The climb is an estimate**, as said above, and so is a saving worked out from it. A small one may not be there on the road.

The 10 is a setting, read for the order and for the roads:

```yaml
routeplanner:
  planning:
    road-metres-per-metre-climbed: 10
```

The routing engine prepares the roads that climb less with it ahead, so after changing it the graph with heights has to be prepared again. The application refuses to start on a graph prepared with another figure, and says which folder to delete.

It is a round figure, and not a fuel estimate. A [study that simulated cars on graded roads](https://vtechworks.lib.vt.edu/handle/10919/46178) put the extra fuel at over 9% for each 1% of slope, and a slope of 1% is a metre climbed in every 100 m of road. The order is not chosen for the vehicle, so one figure serves for every car.

### The fastest order

Send `"fastest": true` and the order is chosen for driving time. Left out, it is chosen for distance. An order that is longer and keeps to faster roads then comes out ahead when it takes less time.

The answer says what the shortest order comes to, in `shortestOrder`, as it does for road climbs. The five stops further up, by road with heights:

| | Order of the stops | Distance | Driving time |
| --- | --- | --- | --- |
| Shortest | Airport, Sylvia Park, Takapuna, Devonport | 76.91 km | 5,063 s |
| Fastest | Takapuna, Devonport, Sylvia Park, Airport | 77.33 km | 4,952 s |

```json
{
  "totalDistanceKm": 77.33,
  "totalDurationSeconds": 4952,
  "shortestOrder": { "distanceKm": 76.91, "durationSeconds": 5063, "climbMetres": 478, "descentMetres": 478, "fuelLitres": null }
}
```

Things to know.

- **Another order has to save a minute to be taken.** The times are for clear roads and the screen gives them to the minute. So when no order is a minute or more quicker than the shortest, the answer is the shortest order, and `shortestOrder` repeats its figures. Without that rule, 103 of the 400 round trips below changed order to save less than a minute, and one went 8.50 km further to save under half a minute.
- **How often it changes the order.** On 400 made-up round trips of four to eight stops around Auckland, between places picked at random from the business names, with 87 minutes of driving at the middle, the order changed in 202. Where it did, it saved 2.5 minutes at the middle, 3% of the trip, and was 1.57 km longer. None saved more than 8.8 minutes, and none was more than 8.75 km longer. On 400 one-way trips the order changed in 148, saving 2.4 minutes at the middle and 10 at most.
- **The times are for clear roads**, as said under "Driving times" above. From the Sky Tower to the Airport they give 21 minutes, where travel guides give about 25 off-peak. At rush hour the motorway the fastest order keeps to can be slower than the streets it avoids.
- **A longer order takes more fuel.** The fuel is worked out for the order chosen, whatever it was chosen for.
- **Not together with road climbs.** Road climbs are counted as road, which the shortest order is chosen for and the fastest is not. A request with both is refused with a 400.
- **On straight lines there is no time to choose by.** The order is chosen for distance as if nothing had been asked, and `shortestOrder` is `null`. It is not an error.
- **It changes the order of the stops and nothing else.** The road between two stops is the one the routing engine picks either way.
- **A stop kept in its place stays there.**

### Fuel

Send the vehicle with the request and every leg says how much fuel it takes, and the plan says the same for the whole trip, in litres:

```json
"vehicle": { "litresPer100Km": 7.5, "kilograms": 1270 }
```

For the five stops above that is 3.87 L: 51.57 km at 7.5 L for every 100 km, on straight lines, which have no hills. Without a `vehicle`, `totalFuelLitres`, `fuelCountsHills` and each leg's `fuelLitres` are `null`.

On roads read with their heights, and with `kilograms`, the fuel counts the hills and `fuelCountsHills` is `true`:

- **A climb costs the work of lifting the car and the driver.** That is their weight times the height, with 75 kg for the driver. The engine turns 30% of the extra fuel into that work, and a litre of petrol gives 33 MJ. So a 1,270 kg car takes about 0.13 L more for every 100 m up. Checked against a measurement on the road: a car climbing an average of 4% uses 1.5 to 2 times its fuel on the level. This gives 1.8 for cars from 1,000 to 1,800 kg.
- **A descent gives that back, but never more than the same stretch takes on the level.** Once the slope does all the pulling, the engine burns next to nothing, and anything steeper is braked away. So a gentle hill gives back on the way down what it cost on the way up, and a steep one costs more than it gives. Measured on the road, descents of 1 to 3% made up for their climbs, and steeper ones did not. Each descent is counted on its own, from one turn of the road to the next, with the road it falls over.
- **A round trip's hills cost little.** It comes down as far as it went up, and most descents are gentle. Measured over 142 drives across Auckland, the hills add 0.2% to the fuel. In Wellington, whose descents are steeper, they add 3%, and in Christchurch 0.1%. On real roads the five stops above take 5.77 L in a medium car at 7.5 L/100 km either way, while the legs run from 3.5% less, down to the airport, to 5.5% more, up from Devonport.
- **A dashboard figure is taken as the figure for level road.** It is an average over the driving the car has done, hills included, and those hills add so little over time that this trip's are counted on top.

The rest:

- **Speed does not change it yet.** A trip that keeps to the motorway takes more or less than this says.
- **The order is chosen as it would be without a vehicle**, unless the least fuel is asked for. See below.
- **Rounded to a hundredth of a litre**, as distances are to a hundredth of a kilometre. The legs can add up to a hundredth more or less than the total.

The screen sends the vehicle saved in the planner and its weight, see "The vehicle" below.

### The least fuel

Send `"leastFuel": true` with a `vehicle` and the order is chosen for the fuel that vehicle takes, counted as under "Fuel" above. Left out, the order is chosen for distance. The hills can be counted only on roads read with their heights, for a vehicle sent with its `kilograms`.

The answer says what the shortest order comes to, in `shortestOrder`, with its `fuelLitres`. The five stops further up, in a medium car at 7.5 L/100 km:

```json
{
  "totalDistanceKm": 76.91,
  "totalFuelLitres": 5.77,
  "fuelCountsHills": true,
  "shortestOrder": { "distanceKm": 76.91, "durationSeconds": 5063, "climbMetres": 478, "descentMetres": 478, "fuelLitres": 5.77 }
}
```

Things to know.

- **Most often the shortest order takes the least fuel.** The fuel goes with the distance, and on a trip that ends where it began most descents give back what the climbs cost. Only a descent too steep to give it all back costs anything on top. On the 400 round trips around Auckland measured for the fastest order, 85 km at the middle, another order took less fuel in 25, and none by more than 0.02 L. On 400 round trips of four to eight stops between businesses in Wellington, whose hills are steeper, 69 changed and none saved more than 0.05 L. Both in a medium car, 1,270 kg at 7.5 L/100 km.
- **Another order has to save a tenth of a litre to be taken**, as the fastest has to save a minute. The screen gives fuel to a tenth of a litre, worked out from a figure that is an average. None of the trips measured did: on 400 round trips and 400 one-way trips in each city, the answer was the shortest order every time. In Wellington it was the same for a small car, 1,020 kg at 5.5 L/100 km, and a large one, 1,570 kg at 10.
- **A one-way trip is not sent to its lowest stop.** A trip that ends lower takes less fuel, but only because the car is left lower, and the next drive up takes that fuel back. Counted as driven, 15 of the 800 one-way trips would have changed order to save 0.1 to 0.33 L, every one by ending 113 to 334 m lower, for up to 2.9 km and 7 minutes more. So the order is chosen for the fuel a trip uses up for good: what its roads take on the level, and what its steep descents brake away. Over a round trip that is the fuel it takes. Another order is taken only when it saves a tenth of a litre both that way and as driven.
- **Without what it needs, the shortest order.** On straight lines, on roads read without heights, and for a vehicle sent without `kilograms`, the fuel goes with the distance and the shortest order takes the least. `shortestOrder` is then `null`. It is not an error.
- **A `vehicle` is needed**, and a request without one is refused with a 400.
- **Not together with the fastest order or road climbs.** The fuel counts the climbs itself. A request with either is refused with a 400.
- **It changes the order of the stops and nothing else.** The road between two stops is the one the routing engine picks either way.
- **Speed does not change the fuel yet**, as said under "Fuel", so the order is chosen for distance and hills alone.
- **A stop kept in its place stays there.**

### Keeping a stop in its place

Some stops cannot go wherever is shortest. The first call of the day that cannot wait, or the last one before heading home. Mark such a stop with `keepInPlace` and it is visited at the turn it was sent in: sent first, visited first. The other stops are arranged around it.

Take the request above and keep the Airport, which was sent second, in its place:

```json
{ "name": "Airport", "latitude": -37.0070, "longitude": 174.7830, "keepInPlace": true }
```

The route becomes Sky Tower, Sylvia Park, **Airport**, Devonport, Takapuna and back to Sky Tower. That is 53.41 km, where 51.57 km was possible with every stop free. `ordersChecked` falls from 24 to 6, because only three stops are left to arrange.

- Keeping a stop can only cost distance or leave it the same. It never saves any.
- With every stop kept there is one order, the one sent, and the answer is that trip.
- On `start` the mark is ignored. The start is first anyway.

### Errors

Validation failures return **400** as RFC 9457 Problem Details, with the media type `application/problem+json`.

```json
{
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

There is no `type`, which RFC 9457 reads as `about:blank`.

| Status | When |
| --- | --- |
| 400 | A field is missing, a coordinate is out of range, there are no stops, there are more than 10, both `fastest` and `factorInRoadClimbs` are true, or `leastFuel` is true without a `vehicle` or with `fastest` or `factorInRoadClimbs` |
| 422 | A place has no road near it, so no car can reach it. Only possible on the `graphhopper` provider |
| 500 | Anything unexpected. The response carries no internal details |

A 422 names the place that cannot be reached, so the caller knows which pin to move:

```json
{
  "title": "Unroutable place",
  "status": 422,
  "detail": "Cannot route to Airport. No road was found near those coordinates. Move the point closer to a street.",
  "instance": "/api/v1/routes/optimize",
  "place": "Airport"
}
```

This is easy to hit in practice. A pin dropped on an airfield, inside a shopping centre or in the middle of a park has no street beside it. Moving it to the nearest road fixes it: the Auckland Airport pin at `-37.0082, 174.7850` fails, while the terminal forecourt at `-37.0070, 174.7830` works.

## POST /api/v1/heights/lookup

How high the ground is at a spot. The map screen asks when a pin's popup opens, and shows the answer under the pin's position.

```json
{ "latitude": -36.8778, "longitude": 174.7639 }
```

```json
{ "heightMetres": 194 }
```

That spot is the top of Maungawhau, Mount Eden, published as 196 m.

| Field | Rules |
| --- | --- |
| `latitude` | Required, -90 to 90 |
| `longitude` | Required, -180 to 180 |

`heightMetres` is in whole metres, below zero below sea level. It is `null` where nothing is known: heights are off, or the spot is outside the height files on this computer. The sea is known, and reads `0`.

It needs heights on, as in "Climb and descent" above, with either provider. It reads the files the roads were read from, in the same way: from the four nearest points of a grid about 30 m apart. It never fetches a file.

Summits read low. The heights are LINZ's 8 m model of 2012, made from 20 m contours, and a summit between two contours is cut short. Measured on 2026-10-15:

| Summit | Published | Read |
| --- | --- | --- |
| Maungawhau, Mount Eden | 196 m | 194 m |
| Maungakiekie, One Tree Hill | 182 m | 173 m |
| Rangitoto | 260 m | 251 m |

A POST and not a GET, so the exact spot travels in the body. A URL is what logs keep. Search rounds its `near` for the same reason, and a height cannot be rounded that far.

A missing field or a coordinate out of range is a 400, with the same body as the other endpoints.

## Saved places

Places somebody named so they can pick them again without typing: home, the office, a shop the map data does not know about. They live in the database, so they are still there after a restart.

There is one list. The application has no users, so whoever can reach it can read the list, and the list may well hold a home address. What keeps it private is in "One app" in [architecture.md](architecture.md).

### GET /api/v1/saved-places

Every saved place, in name order. Capitals and macrons do not change the order.

```json
[
  { "id": 7, "name": "Home", "latitude": -36.9146416667, "longitude": 174.8392303333 },
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
{ "name": "Home", "latitude": -36.9146416667, "longitude": 174.8392303333 }
```

Answers **201** with the place as saved. The `id` is what to remove it by.

```json
{ "id": 7, "name": "Home", "latitude": -36.9146416667, "longitude": 174.8392303333 }
```

A name can be saved once, whatever the capitals. Saving "HOME" while "Home" is there answers **409**, and the place that was there stays as it was:

```json
{
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

## The vehicle

The vehicle the planner works out fuel for, saved so it is given once and not with every trip. Petrol cars only for now. There is one, because there is one driver. The screen sends its fuel use and its weight with every plan, see "Fuel" above.

It is given one of two ways. Entered by hand: its size and what its dashboard shows. Or found in NZTA's vehicle register, see "Cars" below: its make, model, year and engine, which bring the register's figure, and what its dashboard shows if the driver knows it.

### GET /api/v1/vehicle

Entered by hand:

```json
{ "litresPer100Km": 7.5, "figureFrom": "dashboard", "size": "medium", "car": null, "kilograms": 1270 }
```

Found in the register, and going by the register's figure:

```json
{
  "litresPer100Km": 4.8,
  "figureFrom": "register",
  "size": null,
  "car": { "make": "SUZUKI", "model": "SWIFT", "year": 2018, "engineLitres": 1.2 },
  "kilograms": 890
}
```

| Field | Meaning |
| --- | --- |
| `litresPer100Km` | The fuel use plans go by, 2 to 30 |
| `figureFrom` | `dashboard`: what the dashboard shows. `register`: the register's official figure, which real driving exceeds |
| `size` | For a vehicle entered by hand. `null` for a car found in the register |
| `car` | For a car found in the register, as it describes it. `null` for a vehicle entered by hand |
| `kilograms` | What it weighs empty, to the nearest 10 kg, see "The weight" below. `null` when the register no longer lists the car |

Answers **204** with no body until a vehicle is saved. Having none is not an error. The answer is marked `Cache-Control: no-store`, so a vehicle changed on one device is not shown as it was on another.

### PUT /api/v1/vehicle

Saves the vehicle in place of the one there was, and answers with it as above. Entered by hand:

```json
{ "litresPer100Km": 7.5, "size": "medium" }
```

Found in the register, sent back as `/api/v1/cars/variants` gave it. With `litresPer100Km` too, that figure is used instead of the register's:

```json
{ "car": { "make": "SUZUKI", "model": "SWIFT", "year": 2018, "engineLitres": 1.2 } }
```

| Field | Rules |
| --- | --- |
| `litresPer100Km` | 2 to 30. What the dashboard shows. Required with `size`, and may be left out with `car` |
| `size` | `small`: a city hatchback. `medium`: a family hatchback or sedan. `large`: a large car, an SUV or a people mover |
| `car` | `make`, `model`, `year` and `engineLitres`, all required, as the register lists the car |

Send a `size` or a `car`, not both. The size stands in for the weight, which is what a climb costs, and which few drivers know in kilograms. A car found in the register keeps its description when the register is imported again.

### The weight

What a climb costs, see "Fuel" above. Few drivers know it, so it is worked out.

- **Entered by hand, from the size.** Small 1,020 kg, medium 1,270, large 1,570: the middle weight in the register of the cars each size names, counted by how many are on the road. Small is the Swift, Demio, Fit, Vitz, Aqua and their like, medium the Corolla, Axela, Civic, Impreza and Golf, large the RAV4, CX-5, Outlander, Commodore, Camry and Odyssey.
- **Found in the register, from its gross mass.** The register gives no empty weight, and its gross mass is on two bases. A used import from Japan carries Japan's gross weight, the car and 55 kg a seat, so the seats come off. A car sold new here carries the most its maker allows it to weigh loaded. Over the 890 engines the register weighs both ways, that is the car and 36% more in the middle, and half come within 5% of the import's weight. An import rated no more than 200 kg lighter than the same engine sold new is taken to carry its maker's rating too, as a 2015 Hyundai i30 1.8 does at 1,840 kg either way.
- **An engine the register does not weigh** takes the middle weight of the cars with the same engine size. Half the weighed engines come within 6% of what that gives them. 1.3% of the cars listed are such engines, such as the 2011 and 2012 Swift 1.4.

Checked against what makers publish: a 2013 Aqua S comes to 1,065 kg, where Japan's catalogue gives 1,080. A 2019 RAV4 2.5 comes to 1,629, where its maker gives 1,650 with two-wheel drive and 1,695 with four. A 2018 Kia Rio 1.4 comes to 1,176, where its maker gives 1,137 to 1,172. A 2012 Camry 2.5 comes to 1,540, where its maker gives 1,465.

### DELETE /api/v1/vehicle

Answers **204** with no body, whether there was a vehicle or not.

### Errors

| Status | When |
| --- | --- |
| 400 | A missing field, a figure out of range or a size it does not know, as Problem Details with the field named. A size and a car together, or neither, is named `sizeOrCar`, and a size with no figure `litresPer100KmWithSize` |
| 422 | A car the register does not list. The detail says which, such as "NZTA's register has no 2018 SUZUKI SWIFT 1.3 L." |

## Cars

The petrol cars of NZTA's Motor Vehicle Register, for a driver to find theirs in four steps: the make, the model, the year, then the engine. Each step answers with the choices for the next, given the ones made so far. A choice the register does not list gets an empty list, and so does every step until the cars are imported, see the README.

| Request | Answers |
| --- | --- |
| `GET /api/v1/cars/makes` | `["ALFA ROMEO", "ASTON MARTIN", "AUDI", ...]`, in alphabetical order, as the register writes them |
| `GET /api/v1/cars/models?make=SUZUKI` | `["ALTO", "BALENO", ...]`, in alphabetical order |
| `GET /api/v1/cars/years?make=SUZUKI&model=SWIFT` | `[2026, 2025, ...]`, newest first |
| `GET /api/v1/cars/variants?make=SUZUKI&model=SWIFT&year=2018` | Each engine with its figure, smallest first |

```json
[
  { "car": { "make": "SUZUKI", "model": "SWIFT", "year": 2018, "engineLitres": 1.0 }, "litresPer100Km": 5.1, "cars": 545 },
  { "car": { "make": "SUZUKI", "model": "SWIFT", "year": 2018, "engineLitres": 1.2 }, "litresPer100Km": 4.8, "cars": 1896 },
  { "car": { "make": "SUZUKI", "model": "SWIFT", "year": 2018, "engineLitres": 1.4 }, "litresPer100Km": 6.1, "cars": 967 }
]
```

| Field | Meaning |
| --- | --- |
| `car.engineLitres` | To the nearest tenth of a litre, as drivers say it: 1,242 cc is 1.2 |
| `litresPer100Km` | The official figure: the combined figure of the laboratory test the car was approved with, the one most of these cars carry |
| `cars` | How many of these cars are registered, sold new here and imported used together |

A missing `make`, `model` or `year` is a **400**.

### What the figures are

- **Laboratory figures, which real driving exceeds.** NZTA's road tests of 26 light vehicles found real use from 9% lower to 40% higher than the official figures. A dashboard figure is closer, so the screen asks for one too.
- **A used import keeps the figure of the country it came from.** For the same car and engine it reads 1 to 13% lower than one sold new here. A 2018 Swift 1.2 reads 4.2 imported used and 4.8 sold new. A driver is not asked which theirs is, so each engine goes by the figure of whichever has more cars: 4.8 for that Swift, of which 1,344 were sold new and 552 imported used.
- **The makers' own.** A 2020 Corolla GX 2.0 reads 6.0 and a Swift GLX 1.2 4.8, as Toyota and Suzuki published them.
- **A car listed fewer than ten times is left out.** That is where the register's mistakes are, such as a 2005 Mondeo at 2.0 L/100 km or a Corolla with a 57.6 L engine. It leaves 9,342 makes, models, years and engines, for 2,061,242 cars: 74% of the country's petrol cars. Most of the rest are older: under half the cars made before 2004 have a figure at all.
- **Some hybrids are listed as petrol.** An Aqua or a Prius imported used reads 2.6 or 2.7. That is its own figure.
