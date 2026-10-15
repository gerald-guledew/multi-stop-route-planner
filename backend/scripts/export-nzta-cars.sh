#!/bin/sh
# Writes New Zealand's petrol cars to a CSV, from NZTA's Motor Vehicle Register: one row for
# each make, model, year, engine size and origin, with the figures most of those cars carry.
# The API loads that file with --routeplanner.cars.import-enabled=true.
#
# Run it from the backend directory:
#
#     scripts/export-nzta-cars.sh
#
# Needs curl and jq, and no account. The register lists about six million vehicles, but
# NZTA's service adds them up itself, so this fetches a few megabytes. It takes some minutes.
#
# NZTA updates the register every month. Run this again, then the import, to pick that up.

set -eu

# NZTA moves the register to a new address now and then. Its catalogue entry names the current one.
catalogue='https://www.arcgis.com/sharing/rest/content/items/7b4df667d5014f1a93e6050b31d18407?f=json'
entry=$(curl -fsS "$catalogue")
query="$(echo "$entry" | jq -r .url)/0/query"
updated=$(echo "$entry" | jq -r '.modified / 1000 | floor | todate | .[0:10]')

# Petrol cars, first registered here either new or as a used import, with a combined figure a
# petrol car can have. An engine under half a litre, or a year still to come, is a mistake.
cars="VEHICLE_TYPE = 'PASSENGER CAR/VAN' AND MOTIVE_POWER = 'PETROL' AND IMPORT_STATUS IN ('NEW', 'USED') AND FC_COMBINED IS NOT NULL AND CAST(FC_COMBINED AS FLOAT) BETWEEN 2 AND 30 AND CC_RATING >= 500 AND VEHICLE_YEAR <= $(date +%Y)"

# Engine sizes to the nearest tenth of a litre, as drivers say them: 1,242 cc is a 1.2.
groups='MAKE,MODEL,VEHICLE_YEAR,FLOOR((CC_RATING + 50) / 100),IMPORT_STATUS'

# The middle figure of each group, so a few cars entered wrongly do not move it.
median() {
    printf '{"statisticType":"percentile_cont","onStatisticField":"%s","outStatisticFieldName":"%s","statisticParameters":{"value":0.5}}' "$1" "$2"
}
statistics="[{\"statisticType\":\"count\",\"onStatisticField\":\"OBJECTID\",\"outStatisticFieldName\":\"cars\"},$(median 'CAST(FC_COMBINED AS FLOAT)' combined),$(median 'CAST(FC_URBAN AS FLOAT)' urban),$(median 'CAST(FC_EXTRA_URBAN AS FLOAT)' extra_urban),$(median GROSS_VEHICLE_MASS gross_mass),$(median NUMBER_OF_SEATS seats)]"

# Asks the service, and stops the export when it answers with anything but rows.
ask() {
    answer=$(curl -fsS -G "$query" "$@" --data-urlencode 'f=json')
    if ! echo "$answer" | jq -e '.features' > /dev/null; then
        echo "NZTA's service did not answer with cars: $(echo "$answer" | jq -c '.error // .')" >&2
        exit 1
    fi
    echo "$answer"
}

# Written beside the old file and moved over it at the end, so a failed run leaves the old one.
mkdir -p data
partial=data/nzta-cars.csv.partial
makes=data/nzta-makes.partial
trap 'rm -f "$partial" "$makes"' EXIT

# Every car at once is more than the service will add up before it gives up, so one make at a time.
ask --data-urlencode "where=$cars" \
    --data-urlencode 'groupByFieldsForStatistics=MAKE' \
    --data-urlencode 'orderByFields=MAKE' \
    --data-urlencode 'outStatistics=[{"statisticType":"count","onStatisticField":"OBJECTID","outStatisticFieldName":"cars"}]' |
    jq -r '.features[].attributes.MAKE' > "$makes"

echo 'make,model,year,engine_litres,origin,cars,litres_per_100_km,urban_litres_per_100_km,extra_urban_litres_per_100_km,gross_mass_kg,seats' > "$partial"
while IFS= read -r make; do
    printf '%s, ' "$make"
    offset=0
    while :; do
        # Sorted, so two exports of the same month are the same file. Up to 10,000 rows a time,
        # which holds the largest make. A car listed fewer than ten times is left out. That is
        # where the mistakes are, such as a 2005 Mondeo at 2.0 L/100 km or a Corolla with a
        # 57.6 L engine, and it is about 1% of the cars.
        page=$(ask --data-urlencode "where=$cars AND MAKE = '$(echo "$make" | sed "s/'/''/g")'" \
            --data-urlencode "groupByFieldsForStatistics=$groups" \
            --data-urlencode "orderByFields=$groups" \
            --data-urlencode "outStatistics=$statistics" \
            --data-urlencode "resultOffset=$offset" \
            --data-urlencode 'resultRecordCount=10000' \
            --data-urlencode 'maxRecordCountFactor=5')
        echo "$page" | jq -r '
            def tenth: if . == null then null else (. * 10 | round) / 10 end;
            def whole: if . == null then null else round end;
            .features[].attributes
            | select(.cars >= 10)
            | [.MAKE, .MODEL, .VEHICLE_YEAR, .EXPR_1 / 10, .IMPORT_STATUS, .cars,
               (.combined | tenth), (.urban | tenth), (.extra_urban | tenth),
               (.gross_mass | whole), (.seats | whole)]
            | @csv' >> "$partial"
        [ "$(echo "$page" | jq '.exceededTransferLimit // false')" = true ] || break
        offset=$((offset + $(echo "$page" | jq '.features | length')))
    done
done < "$makes"
echo

mv "$partial" data/nzta-cars.csv
echo "Wrote $(($(wc -l < data/nzta-cars.csv) - 1)) makes, models, years and engines to data/nzta-cars.csv, from the register as NZTA updated it on $updated."
