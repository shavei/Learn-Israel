# 03. Content and Curriculum

The curriculum follows the "frame first, anchors next, detail last" rule from
[01-learning-science.md](01-learning-science.md#8-how-people-actually-store-maps-hierarchy-anchors-and-distortion).
Each **tier** is a set of **units**; each unit is a small regional chunk of 4 to 10
places introduced 3 to 5 at a time.

The list below is a starting draft. Every item must be verified against the source
data (see [06-map-data.md](06-map-data.md)) and reviewed by a human before release.

## Item model

Every learnable place is a `Place` with:

| Field | Example | Notes |
|-------|---------|-------|
| `id` | `city.netanya` | Stable, never reused |
| `type` | city, town, region, district, sea, lake, river, mountain, valley, crater, road, site, country | Drives scoring tolerance and rendering |
| `names` | he: נתניה | Plus accepted alternative spellings for typing mode. The model allows more languages later, but only Hebrew ships |
| `geometry` | point / polygon / line | From the map pipeline |
| `tier`, `unit` | 3, `unit.sharon` | Curriculum placement |
| `anchors` | `[city.tel_aviv, city.haifa]` | Places it is introduced relative to |
| `anchorHint` | "On the coast, between Tel Aviv and Hadera" | Shown at Discover |
| `fact` | "Capital of the Sharon, known for its beaches and diamond industry" | One short, memorable line |
| `confusables` | `[city.herzliya, city.kfar_sava]` | Feeds interleaving |
| `prereqs` | `[region.sharon]` | Unlock gating |
| `importance` | 1 to 5 | Orders introduction within a unit |

## Tier 0: The frame (about 15 items)

Taught first; everything else hangs on it.

- **Neighbors:** Lebanon, Syria, Jordan, Egypt.
- **Waters:** Mediterranean Sea, Red Sea / Gulf of Eilat, Sea of Galilee (Kinneret),
  Dead Sea, Jordan River.
- **Shape cues:** the coastline, the "tip" at Eilat, the northern "finger" (Galilee
  panhandle), the Golan Heights.
- **The big three anchors:** Jerusalem, Tel Aviv-Yafo, Haifa.
- **Bottom anchor:** Eilat. **Desert anchor:** Beersheba.

## Tier 1: Regions and districts (26 items, built in Phase 1b)

Built as 19 natural regions that split the land without overlapping, each a group of
the official CBS natural regions (the list below, with the Coastal Plain split into the
Haifa region, the Carmel coast, the Sharon, Gush Dan and the southern coastal plain,
plus Western Galilee and the Menashe Plateau), and 7 districts. See `content/regions.yaml`.


- **Natural regions:** Upper Galilee, Lower Galilee, Golan Heights, Hula Valley,
  Jezreel Valley, Carmel, Coastal Plain, Sharon, Gush Dan (metro), Shephelah, Judean
  Mountains, Samaria, Judean Desert, Jordan Valley, Negev, Arava.
- **Administrative districts:** Northern, Haifa, Central, Tel Aviv, Jerusalem,
  Southern (plus a clearly labeled layer for the Judea and Samaria Area; see policy
  section below).

## Tier 2: Anchor cities (about 15 items)

The largest and most "structural" cities, chosen so that every region has at least
one anchor: Jerusalem, Tel Aviv-Yafo, Haifa, Rishon LeZion, Petah Tikva, Ashdod,
Netanya, Beersheba, Ashkelon, Nazareth, Tiberias, Eilat, Kiryat Shmona, Modi'in,
Hadera.

## Tier 3: Cities and towns by unit (about 80 items)

Example units (each 4 to 10 places):

| Unit | Places |
|------|--------|
| Gush Dan core | Ramat Gan, Givatayim, Bnei Brak, Holon, Bat Yam, Herzliya, Ramat HaSharon |
| Gush Dan ring | Rehovot, Ness Ziona, Lod, Ramla, Rosh HaAyin, Kfar Saba, Ra'anana, Hod HaSharon |
| Sharon | Netanya, Hadera, Even Yehuda, Kfar Yona, Pardes Hanna-Karkur, Zikhron Ya'akov |
| Haifa Bay and Carmel | Kiryat Ata, Kiryat Bialik, Kiryat Motzkin, Kiryat Yam, Nesher, Tirat Carmel, Daliyat al-Karmel |
| Western Galilee | Akko, Nahariya, Karmiel, Ma'alot-Tarshiha, Shlomi |
| Upper Galilee and Hula | Safed, Kiryat Shmona, Rosh Pina, Metula, Hatzor HaGlilit |
| Lower Galilee and Kinneret | Tiberias, Nazareth, Nof HaGalil, Migdal HaEmek, Sakhnin, Kafr Kanna |
| Jezreel and Gilboa | Afula, Beit She'an, Yokne'am, Umm al-Fahm |
| Golan | Katzrin, Majdal Shams, Mas'ade |
| Jerusalem area | Beit Shemesh, Mevaseret Zion, Ma'ale Adumim, Abu Ghosh |
| Shephelah and South coast | Kiryat Gat, Kiryat Malakhi, Gedera, Yavne, Sderot, Netivot, Ofakim |
| Negev | Dimona, Arad, Yeruham, Mitzpe Ramon, Rahat |
| Arava and Eilat | Eilat, Yotvata, Ein Yahav |

## Tier 4: Nature (about 40 items)

- **Mountains and ridges:** Mount Hermon, Mount Meron, Mount Tabor, Mount Carmel,
  Mount Gilboa, Mount Arbel, Judean Mountains.
- **Craters (makhteshim):** Ramon, Gadol, Katan.
- **Rivers and streams:** Jordan, Yarkon, Kishon, Alexander, Dan, Banias, Hasbani,
  Ayalon, Besor.
- **Valleys and plains:** Hula, Jezreel, Beit She'an, Jordan Valley, Arava, Ayalon.
- **Nature reserves and oases:** Ein Gedi, Hula Lake reserve, Ein Avdat, Timna.

## Tier 5: Infrastructure (about 15 items)

- **Highways:** Route 1 (Tel Aviv to Jerusalem), Route 2 (coastal), Route 4,
  Route 6 (Trans-Israel), Route 40, Route 90 (the long north-south road).
- **Airports:** Ben Gurion (TLV), Ramon (ETM), Haifa.
- **Ports:** Haifa, Ashdod, Eilat.

## Tier 6: Heritage and tourism (about 25 items)

Masada, Caesarea, Megiddo, Akko Old City, Jaffa, Old City of Jerusalem, Western Wall,
Qumran, Beit She'arim, Bahá'í Gardens (Haifa), Capernaum, Nimrod Fortress, Avdat,
Tel Dan, Rosh HaNikra, Herodion, Bet Guvrin, Mamshit, Yad Vashem, and more. Each
gets a richer fact card.

## Tier 7: Deep cuts (open ended, 300+ items)

Every locality from the CBS list above a population threshold, selected kibbutzim and
moshavim, wadis, junctions. Unlocked per region after its Tier 3 unit is mastered.
This is where long-term players live.

## Ordering algorithm

Within the unlocked set, the next new place is chosen by:

1. All `prereqs` mastered (stability above threshold).
2. Highest `importance` first.
3. Prefer places whose `anchors` are well known (strong scaffold).
4. Do not introduce two members of a confusable pair in the same session; introduce
   the second only once the first is stable, then interleave them.

## Writing facts

- One line, max about 90 characters, concrete and spatial when possible.
- Tie to meaning: "why it is here" beats trivia.
- Neutral and factual tone. No politics in fact lines.
- Each fact written and reviewed in Hebrew by a native speaker.

## Policy: borders and contested areas

Israel's map involves lines with different legal and political status (the 1949
Armistice "Green Line", the West Bank / Judea and Samaria, Gaza, the Golan Heights,
East Jerusalem). **Decision** (see [10-risks-and-open-questions.md](10-risks-and-open-questions.md)):

- Draw the **de facto lines of control** as the main map outline, matching what
  players experience on the ground and in navigation apps.
- Provide an optional **"Green Line" layer** with a neutral explanatory note.
- Label areas with their commonly used Hebrew names, and include alternate names in
  the item data.
- Keep fact lines geographic, not political.
