package com.solvelify.routeplanner.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Search against a real PostgreSQL, because the parts being tested are PostgreSQL's: the
 * trigram index, the similarity ranking and the function that drops accents. An in-memory
 * database would prove nothing.
 *
 * <p>Rows are inserted with SQL, and rolled back after each test. Nothing in the application
 * writes addresses or places one at a time, only the bulk importers do.
 */
@SpringBootTest
@Transactional
class PostgresPlaceSearchTest {

    private static final ReferencePoint REMUERA = new ReferencePoint(-36.8750, 174.7900);
    private static final ReferencePoint EPSOM = new ReferencePoint(-36.8800, 174.7800);
    private static final ReferencePoint HENDERSON = new ReferencePoint(-36.8700, 174.6300);
    private static final ReferencePoint QUEEN_STREET = new ReferencePoint(-36.8460, 174.7660);
    private static final ReferencePoint NORTHCOTE = new ReferencePoint(-36.7990, 174.7460);
    private static final ReferencePoint PAPAKURA = new ReferencePoint(-37.0650, 174.9430);
    private static final ReferencePoint REEFTON = new ReferencePoint(-42.1100, 171.8600);
    private static final ReferencePoint INVERCARGILL = new ReferencePoint(-46.4130, 168.3540);

    @Autowired
    private PlaceSearch placeSearch;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void insertAddressesAndPlaces() {
        insertAddress(1, "20A Bassett Road, Remuera", -36.8712, 174.7866);
        insertAddress(2, "2/286 Mount Wellington Highway, Mount Wellington", -36.9170, 174.8409);
        insertAddress(3, "12 Bassett Street, Invercargill", -46.4300, 168.3800);
        insertAddress(4, "100 Broadway, Newmarket", -36.8700, 174.7770);
        insertAddress(5, "12 Bank Street, Whangārei", -35.7251, 174.3237);
        insertAddress(6, "5 O'Neill Street, Ponsonby, Auckland", -36.8540, 174.7440);
        insertAddress(7, "1 The Warehouse Way, Northcote, Auckland", -36.7990, 174.7460);
        insertAddress(8, "277 Broadway, Newmarket, Auckland", -36.8690, 174.7780);
        insertAddress(9, "24 McDonalds Road, Inangahua, Reefton", -42.1100, 171.8600);

        insertPoi("nw", "New World Remuera", "10 Clonbern Rd", "Remuera", "Auckland", 0.99, -36.8817, 174.7975);
        insertPoi("wh", "The Warehouse", "64-74 Broadway", "Newmarket", "Auckland", 0.99, -36.8720, 174.7770);
        insertPoi("cw", "Chemist Warehouse", "219 Broadway", "Newmarket", "Auckland", 0.80, -36.8710, 174.7775);
        insertPoi("hm", "H&M", "277 Broadway, Westfield Newmarket", "Newmarket", "Auckland", 0.99, -36.8691, 174.7781);
        insertPoi("mc", "McDonald's", "56 Queen St", "Auckland Central", "Auckland", 0.99, -36.8460, 174.7660);
        insertPoi("ps", "PAK'nSAVE Mangere", "44 Orly Ave", "Māngere", "Auckland", 0.99, -36.9720, 174.7900);
        insertPoi("k1", "KFC", "1 Great South Rd", "Epsom", "Auckland", 0.60, -36.8800, 174.7800);
        insertPoi("k2", "KFC", "9 Lincoln Rd", "Henderson", "Auckland", 0.95, -36.8700, 174.6300);
        insertPoi("gh", "Ghost Kitchen Remuera", "1 Nowhere St", "Remuera", "Auckland", 0.10, -36.8800, 174.7900);
    }

    // Addresses: what worked before named places arrived still works.

    @Test
    void findsAnAddressFromPartOfIt() {
        assertThat(names(placeSearch.search("bassett road remuera", 8)))
                .contains("20A Bassett Road, Remuera");
    }

    @Test
    void putsTheClosestAddressFirst() {
        assertThat(placeSearch.search("bassett road remuera", 8).getFirst().name())
                .isEqualTo("20A Bassett Road, Remuera");
    }

    @Test
    void doesNotConfuseTheInvercargillStreetWithTheAucklandRoad() {
        assertThat(names(placeSearch.search("bassett road remuera", 8)))
                .doesNotContain("12 Bassett Street, Invercargill");
    }

    @Test
    void findsAMacronNameTypedWithoutTheMacron() {
        // Most keyboards have no macron key, so this is how Whangārei gets typed.
        assertThat(names(placeSearch.search("bank street whangarei", 8)))
                .containsExactly("12 Bank Street, Whangārei");
    }

    @Test
    void findsTheSameAddressTypedWithTheMacron() {
        assertThat(names(placeSearch.search("bank street whangārei", 8)))
                .containsExactly("12 Bank Street, Whangārei");
    }

    @Test
    void findsAnApostropheNameTypedWithoutTheApostrophe() {
        assertThat(names(placeSearch.search("oneill street", 8)))
                .containsExactly("5 O'Neill Street, Ponsonby, Auckland");
    }

    @Test
    void marksAnAddressAsAnAddressAndCarriesItsCoordinates() {
        FoundPlace found = placeSearch.search("100 Broadway", 1).getFirst();

        assertThat(found.kind()).isEqualTo(FoundPlace.Kind.ADDRESS);
        assertThat(found.detail()).isNull();
        assertThat(found.latitude()).isEqualTo(-36.8700);
        assertThat(found.longitude()).isEqualTo(174.7770);
    }

    // Named places.

    @Test
    void findsABusinessByItsName() {
        FoundPlace found = placeSearch.search("new world remuera", 8).getFirst();

        assertThat(found.kind()).isEqualTo(FoundPlace.Kind.POI);
        assertThat(found.name()).isEqualTo("New World Remuera");
        assertThat(found.detail()).isEqualTo("10 Clonbern Rd, Remuera, Auckland");
        assertThat(found.latitude()).isEqualTo(-36.8817);
        assertThat(found.longitude()).isEqualTo(174.7975);
    }

    @Test
    void findsABusinessByItsSuburbWhenTheNameDoesNotSayIt() {
        // "The Warehouse" has no suburb in its name. The suburb comes from the nearest address.
        assertThat(placeSearch.search("warehouse newmarket", 8).getFirst().name())
                .isEqualTo("The Warehouse");
    }

    @Test
    void putsABusinessAheadOfAStreetNamedLikeIt() {
        List<String> found = names(placeSearch.search("the warehouse", 8));

        assertThat(found.getFirst()).isEqualTo("The Warehouse");
        assertThat(found).contains("1 The Warehouse Way, Northcote, Auckland");
    }

    @Test
    void putsAnAddressAheadOfTheBusinessesAtThatAddress() {
        List<String> found = names(placeSearch.search("277 broadway", 8));

        assertThat(found).containsExactly("277 Broadway, Newmarket, Auckland", "H&M");
    }

    @Test
    void findsABusinessTypedWithoutItsApostrophe() {
        List<String> found = names(placeSearch.search("mcdonalds", 8));

        assertThat(found).containsExactly("McDonald's", "24 McDonalds Road, Inangahua, Reefton");
    }

    @Test
    void findsABusinessInASuburbTypedWithoutItsMacron() {
        assertThat(names(placeSearch.search("paknsave mangere", 8))).containsExactly("PAK'nSAVE Mangere");
    }

    @Test
    void leavesOutPlacesTheSourceIsUnsureExist() {
        assertThat(names(placeSearch.search("ghost kitchen", 8))).isEmpty();
    }

    @Test
    void putsTheMoreCertainOfTwoEqualMatchesFirst() {
        List<FoundPlace> found = placeSearch.search("kfc", 8);

        assertThat(found).extracting(FoundPlace::detail)
                .containsExactly("9 Lincoln Rd, Henderson, Auckland", "1 Great South Rd, Epsom, Auckland");
    }

    @Test
    void treatsACommaLikeASpace() {
        assertThat(names(placeSearch.search("new world, remuera", 8))).containsExactly("New World Remuera");
    }

    @Test
    void treatsAnUnderscoreAsAnOrdinaryCharacter() {
        // To LIKE an underscore means "any one character", which would match "Bank".
        assertThat(placeSearch.search("b_nk street", 8)).isEmpty();
    }

    @Test
    void treatsAPercentSignAsAnOrdinaryCharacter() {
        // To LIKE a percent sign means "anything", which would match every row.
        assertThat(placeSearch.search("%%%", 8)).isEmpty();
    }

    @Test
    void treatsWhatAPatternWouldReadAsAnOrdinaryCharacter() {
        // To a regular expression a full stop means "any one character", which would match "Bank".
        assertThat(placeSearch.search("b.nk street", 8)).isEmpty();
        assertThat(placeSearch.search("b[a]nk street", 8)).isEmpty();
        assertThat(placeSearch.search("bank|broadway", 8)).isEmpty();
        assertThat(placeSearch.search("^12 bank", 8)).isEmpty();
    }

    @Test
    void searchesForWhatWouldNotBeAPatternAtAllWithoutFailing() {
        for (String typed : List.of("***", "(((", ")))", "[[[", "]]]", "{2}", "+++", "???", "|||", "\\\\\\", "$$$",
                "bank (street", "bank street)", "a\\", "*bank", "(?i)bank", "***=bank")) {
            assertThat(placeSearch.search(typed, 8)).as(typed).isEmpty();
        }
    }

    @Test
    void readsASymbolTheDatabaseWouldTurnIntoPartOfAPatternAsASpace() {
        // Dropping accents rewrites these as an asterisk, "(C)" and three full stops.
        assertThat(names(placeSearch.search("bank × street", 8))).containsExactly("12 Bank Street, Whangārei");
        assertThat(names(placeSearch.search("bank © street … whangarei", 8)))
                .containsExactly("12 Bank Street, Whangārei");
    }

    @Test
    void looksForNoCharacterThatDroppingAccentsTurnsIntoPartOfAPattern() {
        List<Integer> rewritten = jdbc.sql("""
                        SELECT character
                        FROM generate_series(128, 262143) AS character
                        WHERE character NOT BETWEEN 55296 AND 57343
                          AND searchable(chr(character)) ~ '[\\\\^$.|?*+()\\[\\]{}]'
                        """)
                .query(Integer.class)
                .list();

        // The multiplication sign is one of them. If the list were empty the test would prove nothing.
        assertThat(rewritten).contains((int) '×');
        assertThat(rewritten).noneMatch(SearchTerm::isLookedFor);
    }

    @Test
    void reliesOnPlainPunctuationComingThroughAsItWasTyped() {
        List<Integer> changed = jdbc.sql("""
                        SELECT character
                        FROM generate_series(33, 126) AS character
                        WHERE searchable(chr(character)) <> chr(character)
                        """)
                .query(Integer.class)
                .list();

        // The two apostrophes that searchable() drops, and nothing else.
        assertThat(changed).containsExactly((int) '\'', (int) '`');
    }

    @Test
    void findsANameTypedWithACurledApostrophe() {
        // A phone keyboard turns the apostrophe into this one.
        assertThat(names(placeSearch.search("mcdonald’s", 8)))
                .containsExactly("McDonald's", "24 McDonalds Road, Inangahua, Reefton");
    }

    // Words written two ways.

    @Test
    void findsARoadTypedAsRd() {
        assertThat(names(placeSearch.search("bassett rd remuera", 8)))
                .containsExactly("20A Bassett Road, Remuera");
    }

    @Test
    void ranksAnAddressTheSameWhicheverWayItsRoadWasTyped() {
        insertAddress(30, "12 Bassett Road, Remuera", -36.8710, 174.7860);
        insertAddress(31, "112 Bassett Road, Remuera", -36.8750, 174.7900);

        // The search looks from outside number 112, which only contains what was typed.
        List<String> typedInFull = names(placeSearch.search("12 bassett road", REMUERA, 8));

        assertThat(typedInFull).containsExactly("12 Bassett Road, Remuera", "112 Bassett Road, Remuera");
        assertThat(names(placeSearch.search("12 bassett rd", REMUERA, 8))).isEqualTo(typedInFull);
    }

    @Test
    void findsASuburbWrittenInFullFromItsAbbreviation() {
        insertAddress(32, "5 Tamaki Drive, Saint Heliers, Auckland", -36.8500, 174.8600);
        insertAddress(33, "7 Mount Eden Road, Mount Eden, Auckland", -36.8870, 174.7570);

        assertThat(names(placeSearch.search("tamaki drive st heliers", 8)))
                .containsExactly("5 Tamaki Drive, Saint Heliers, Auckland");
        assertThat(names(placeSearch.search("7 mt eden rd", 8)))
                .containsExactly("7 Mount Eden Road, Mount Eden, Auckland");
    }

    @Test
    void findsARoadWrittenShortFromItsNameInFull() {
        // LINZ writes these two road names short, and the suburb of the second in full.
        insertAddress(34, "9 St Lukes Road, Mount Albert, Auckland", -36.8840, 174.7200);
        insertAddress(35, "3 Mt Pleasant Road, Mount Pleasant, Christchurch", -43.5630, 172.7250);

        assertThat(names(placeSearch.search("saint lukes road", 8)))
                .containsExactly("9 St Lukes Road, Mount Albert, Auckland");
        assertThat(names(placeSearch.search("mount pleasant road", 8)))
                .containsExactly("3 Mt Pleasant Road, Mount Pleasant, Christchurch");
    }

    @Test
    void readsAWordTypedInFullAsItsAbbreviationOnlyWhereThatStandsAsAWord() {
        insertAddress(34, "9 St Lukes Road, Mount Albert, Auckland", -36.8840, 174.7200);
        // "st" begins a word in the first of these and ends one in the second.
        insertAddress(36, "4 Stanley Avenue, Lukes Bay, Auckland", -36.9000, 174.9000);
        insertAddress(38, "6 West Lukes Way, Auckland", -36.9100, 174.9100);

        assertThat(names(placeSearch.search("saint lukes", 8)))
                .containsExactly("9 St Lukes Road, Mount Albert, Auckland");

        // Typed short it is looked for as letters anywhere, as every word is.
        assertThat(names(placeSearch.search("st lukes", 8))).containsExactlyInAnyOrder(
                "9 St Lukes Road, Mount Albert, Auckland",
                "4 Stanley Avenue, Lukes Bay, Auckland",
                "6 West Lukes Way, Auckland");
    }

    @Test
    void stillFindsAStreetFromSt() {
        insertAddress(20, "12 Queen Street, Papakura", -37.0650, 174.9430);

        assertThat(names(placeSearch.search("12 queen st", 8))).containsExactly("12 Queen Street, Papakura");
    }

    @Test
    void leavesOutAFullStopAfterAnAbbreviation() {
        insertAddress(34, "9 St Lukes Road, Mount Albert, Auckland", -36.8840, 174.7200);

        assertThat(names(placeSearch.search("9 st. lukes rd.", 8)))
                .containsExactly("9 St Lukes Road, Mount Albert, Auckland");
    }

    @Test
    void listsTheBusinessAtAnAddressWhoseRoadItWritesShort() {
        insertAddress(37, "9 Lincoln Road, Henderson, Auckland", -36.8700, 174.6300);

        // The KFC there gives its address as "9 Lincoln Rd".
        assertThat(names(placeSearch.search("9 lincoln road", 8)))
                .containsExactly("9 Lincoln Road, Henderson, Auckland", "KFC");
    }

    @Test
    void listsTheBusinessAtAnAddressWhoseStreetItWritesShort() {
        insertAddress(39, "56 Queen Street, Auckland Central, Auckland", -36.8460, 174.7660);

        // "st" is the start of "street", so typing it short always found both. Typed in full,
        // "street" is nowhere in the "56 Queen St" the restaurant gives.
        assertThat(names(placeSearch.search("56 queen street", 8)))
                .containsExactly("56 Queen Street, Auckland Central, Auckland", "McDonald's");
        assertThat(names(placeSearch.search("56 queen st", 8)))
                .containsExactly("56 Queen Street, Auckland Central, Auckland", "McDonald's");
    }

    @Test
    void findsABusinessNamedWithAnAbbreviationFromTheWordInFull() {
        insertPoi("me", "Mt Eden Village Dairy", "400 Mt Eden Rd", "Mount Eden", "Auckland", 0.99, -36.8870, 174.7570);

        assertThat(names(placeSearch.search("mount eden village", 8))).containsExactly("Mt Eden Village Dairy");
    }

    @Test
    void findsABusinessByItsSuburbTypedShort() {
        // The name does not say where it is, and the suburb is written in full.
        insertPoi("wm", "The Warehouse", "100 Dominion Rd", "Mount Eden", "Auckland", 0.99, -36.8870, 174.7570);

        assertThat(placeSearch.search("warehouse mt eden", 8)).extracting(FoundPlace::detail)
                .containsExactly("100 Dominion Rd, Mount Eden, Auckland");
    }

    @Test
    void countsANameWrittenTheOtherWayAsTheSameMatch() {
        // Two kilometres apart. If the spelling that was not typed counted for less, the
        // place two kilometres off would lead.
        ReferencePoint outsideTheFirst = new ReferencePoint(-36.8840, 174.7200);
        ReferencePoint outsideTheSecond = new ReferencePoint(-36.8840, 174.7425);
        insertPoi("s1", "St Lukes Pharmacy", "1 Nowhere Lane", "Mount Albert", "Auckland", 0.99, -36.8840, 174.7200);
        insertPoi("s2", "Saint Lukes Pharmacy", "2 Nowhere Lane", "Mount Albert", "Auckland", 0.99, -36.8840, 174.7425);

        assertThat(names(placeSearch.search("st lukes pharmacy", outsideTheSecond, 8)))
                .containsExactly("Saint Lukes Pharmacy", "St Lukes Pharmacy");
        assertThat(names(placeSearch.search("saint lukes pharmacy", outsideTheFirst, 8)))
                .containsExactly("St Lukes Pharmacy", "Saint Lukes Pharmacy");
    }

    @Test
    void countsASpellingThroughAMacronToo() {
        ReferencePoint outsideTheSecond = new ReferencePoint(-36.9700, 174.8125);
        insertPoi("m1", "St Lukes Bakery Māngere", "1 Nowhere Lane", "Māngere", "Auckland", 0.99, -36.9700, 174.7900);
        insertPoi("m2", "Saint Lukes Bakery Māngere", "2 Nowhere Lane", "Māngere", "Auckland", 0.99, -36.9700, 174.8125);

        assertThat(names(placeSearch.search("st lukes bakery māngere", outsideTheSecond, 8)))
                .containsExactly("Saint Lukes Bakery Māngere", "St Lukes Bakery Māngere");
    }

    @Test
    void putsTheAddressTypedAheadOfOneThatHoldsEverySpelling() {
        // The second holds "mt eden" in its road and "mount eden" in its suburb, after a 9.
        insertAddress(40, "9 Mt Eden Road, Epsom, Auckland", -36.8870, 174.7700);
        insertAddress(41, "19 Mt Eden Road, Mount Eden, Auckland", -36.8870, 174.7570);

        assertThat(names(placeSearch.search("9 mt eden", 8)))
                .containsExactly("9 Mt Eden Road, Epsom, Auckland", "19 Mt Eden Road, Mount Eden, Auckland");
    }

    @Test
    void ranksAnAddressOnTheBestOfTheSpellingsItHolds() {
        // The first begins with the words in one spelling, and only contains them in the other.
        // The second only contains them, in the spelling that was typed.
        insertAddress(42, "9 Mount Eden Road, Mt Eden, Auckland", -36.8870, 174.7570);
        insertAddress(43, "2/9 Mt Eden Road, Epsom, Auckland", -36.8870, 174.7700);

        assertThat(names(placeSearch.search("9 mt eden", 8)))
                .containsExactly("9 Mount Eden Road, Mt Eden, Auckland", "2/9 Mt Eden Road, Epsom, Auckland");
    }

    @Test
    void ranksAPlaceOnTheBestOfTheSpellingsItHolds() {
        // The dairy is in Mount Eden, which is all of what was typed in the other spelling. As
        // typed, its name and suburb hold only "eden", and the cafe would lead on being one
        // letter out.
        insertPoi("d1", "Mount Dairy", "5 Mt Eden Rd", "Mount Eden", "Auckland", 0.99, -36.8870, 174.7570);
        insertPoi("d2", "Mt Edem Cafe", "1 Eden St", "Timaru", "Timaru", 0.99, -44.3960, 171.2540);

        assertThat(names(placeSearch.search("mt eden", 8))).containsExactly("Mount Dairy", "Mt Edem Cafe");
    }

    // A place found by where it is, and not by its name.

    @Test
    void putsAPlaceNamedForASuburbAheadOfOneThatIsMerelyInIt() {
        insertPoi("ns", "Newmarket Train Station", "Remuera Rd", "Newmarket", "Auckland", 0.99, -36.8695, 174.7785);

        // H&M is in Newmarket too. Its name is so short that "newmarket" is most of what it is
        // compared on, which would put it first.
        assertThat(names(placeSearch.search("newmarket", 8)).getFirst()).isEqualTo("Newmarket Train Station");
    }

    @Test
    void doesNotLetANearerPlaceLeadForMerelyBeingInTheSuburb() {
        // One kilometre apart, and the search is looking from outside the Mobil.
        ReferencePoint outsideTheMobil = new ReferencePoint(-36.8500, 174.8600);
        insertPoi("mo", "Mobil", "350 St Heliers Bay Rd", "Saint Heliers", "Auckland", 0.99, -36.8500, 174.8600);
        insertPoi("ha", "Saint Heliers Hall", "1 Nowhere Lane", "Saint Heliers", "Auckland", 0.99, -36.8500, 174.8715);

        assertThat(names(placeSearch.search("st heliers", outsideTheMobil, 8)))
                .containsExactly("Saint Heliers Hall", "Mobil");
    }

    @Test
    void doesNotTakeAWordThatHoldsAnAbbreviationForTheAbbreviation() {
        // Both are in Saint Johns, a kilometre nearer than the hall. "st" begins a word of the
        // first name and ends a word of the second.
        ReferencePoint outsideTheStudio = new ReferencePoint(-36.8750, 174.8400);
        insertPoi("ds", "Design Studio", "1 Nowhere Lane", "Saint Johns", "Auckland", 0.99, -36.8750, 174.8400);
        insertPoi("mr", "Meadowbank Roast", "2 Nowhere Lane", "Saint Johns", "Auckland", 0.99, -36.8750, 174.8400);
        insertPoi("jh", "St Johns Hall", "3 Nowhere Lane", "Saint Johns", "Auckland", 0.99, -36.8750, 174.8515);

        assertThat(names(placeSearch.search("st johns", outsideTheStudio, 8)).getFirst()).isEqualTo("St Johns Hall");
    }

    @Test
    void takesAWordThatBeginsWithTheFirstWordForIt() {
        // "pak" is not a word of the name, only the beginning of one.
        ReferencePoint inMangere = new ReferencePoint(-36.9720, 174.7900);
        insertPoi("pk", "Pak Lane Motors", "1 Nowhere Lane", "Papakura", "Auckland", 0.99, -37.0650, 174.9430);

        assertThat(names(placeSearch.search("pak", inMangere, 8)).getFirst()).isEqualTo("PAK'nSAVE Mangere");
    }

    @Test
    void keepsAPlaceInFullWhenTheSearchBeganWithItsName() {
        ReferencePoint outsideTheHall = new ReferencePoint(-36.8500, 174.8715);
        insertPoi("mo", "Mobil", "350 St Heliers Bay Rd", "Saint Heliers", "Auckland", 0.99, -36.8500, 174.8600);
        insertPoi("mh", "Mobil Saint Heliers", "2 Nowhere Lane", "Saint Heliers", "Auckland", 0.99, -36.8500, 174.8715);

        // Both are all of what was typed. Were the first marked down for not naming its suburb,
        // the nearer one would not lead from outside it.
        assertThat(placeSearch.search("mobil st heliers", outsideTheHall, 8)).extracting(FoundPlace::detail)
                .containsExactly("2 Nowhere Lane, Saint Heliers, Auckland", "350 St Heliers Bay Rd, Saint Heliers, Auckland");
        assertThat(placeSearch.search("mobil st heliers", new ReferencePoint(-36.8500, 174.8600), 8))
                .extracting(FoundPlace::detail)
                .containsExactly("350 St Heliers Bay Rd, Saint Heliers, Auckland", "2 Nowhere Lane, Saint Heliers, Auckland");
    }

    @Test
    void marksDownAPlaceThatIsMerelyInASuburbTypedWithItsMacron() {
        insertPoi("zm", "Z", "1 Nowhere Lane", "Māngere", "Auckland", 0.99, -36.9720, 174.7900);

        assertThat(names(placeSearch.search("māngere", 8))).containsExactly("PAK'nSAVE Mangere", "Z");
    }

    // Limits.

    @Test
    void ignoresSearchesTooShortToMeanAnything() {
        assertThat(placeSearch.search("ba", 8)).isEmpty();
    }

    @Test
    void respectsTheLimitAcrossAddressesAndBusinesses() {
        // Five rows mention Newmarket: two addresses and three places.
        assertThat(placeSearch.search("newmarket", 3)).hasSize(3);
    }

    @Test
    void ranksNoMoreCandidatesFromEachTableThanItIsToldTo() {
        PlaceSearch capped = new PostgresPlaceSearch(jdbc, new SearchProperties(0.3, 1, 1));

        // One address and one place get through to be ranked, however many matched.
        assertThat(capped.search("newmarket", 8)).hasSize(2);
    }

    // Nearest first, when the search says where it is looking from.

    @Test
    void putsTheNearerOfTwoBranchesFirst() {
        // Without a reference point the Henderson branch leads, because the data is surer of it.
        assertThat(placeSearch.search("kfc", EPSOM, 8)).extracting(FoundPlace::detail)
                .containsExactly("1 Great South Rd, Epsom, Auckland", "9 Lincoln Rd, Henderson, Auckland");

        assertThat(placeSearch.search("kfc", HENDERSON, 8)).extracting(FoundPlace::detail)
                .containsExactly("9 Lincoln Rd, Henderson, Auckland", "1 Great South Rd, Epsom, Auckland");
    }

    @Test
    void treatsABranchNamedAfterItsSuburbAsTheSameMatchAsOneNamedPlainly() {
        insertPoi("nwp", "New World", "6 Averill St", "Papakura", "Auckland", 0.99, -37.0650, 174.9430);

        // The words are all of one name and half of the other, and the two are about 25 km apart.
        // If the shorter name counted for more, it would lead from Remuera as well.
        assertThat(names(placeSearch.search("new world", REMUERA, 8)))
                .containsExactly("New World Remuera", "New World");
        assertThat(names(placeSearch.search("new world", PAPAKURA, 8)))
                .containsExactly("New World", "New World Remuera");
    }

    @Test
    void putsTheNameTheWordsAccountForMostOfFirstWhenNothingSaysWhereToLook() {
        insertPoi("nwp", "New World", "6 Averill St", "Papakura", "Auckland", 0.99, -37.0650, 174.9430);

        assertThat(names(placeSearch.search("new world", 8)))
                .containsExactly("New World", "New World Remuera");
    }

    @Test
    void findsANameFromALastWordThatIsNotFinishedYet() {
        // Under two kilometres from the restaurant, and "McDonald" is a whole word of its name.
        insertPoi("mm", "McDonald Metals", "5 Stanley St", "Parnell", "Auckland", 0.99, -36.8560, 174.7800);

        assertThat(names(placeSearch.search("mcdonald", QUEEN_STREET, 8)))
                .containsExactly("McDonald's", "McDonald Metals", "24 McDonalds Road, Inangahua, Reefton");
    }

    @Test
    void putsABusinessAShortDriveAwayAheadOfTheStreetNextDoor() {
        // The Warehouse is just under nine kilometres from The Warehouse Way.
        assertThat(names(placeSearch.search("the warehouse", NORTHCOTE, 8)))
                .containsExactly("The Warehouse", "1 The Warehouse Way, Northcote, Auckland");
    }

    @Test
    void putsTheStreetNextDoorAheadOfABusinessAtTheOtherEndOfTheCountry() {
        assertThat(names(placeSearch.search("mcdonalds", REEFTON, 8)))
                .containsExactly("24 McDonalds Road, Inangahua, Reefton", "McDonald's");
    }

    @Test
    void putsTheNumberTypedAheadOfANearerAddressThatOnlyContainsIt() {
        // Number 12 is 29 km from number 112, far enough that distance alone would lose it the lead.
        insertAddress(20, "12 Queen Street, Papakura", -37.0650, 174.9430);
        insertAddress(21, "112 Queen Street, Auckland Central, Auckland", -36.8500, 174.7650);
        ReferencePoint outsideNumber112 = new ReferencePoint(-36.8500, 174.7650);

        assertThat(names(placeSearch.search("12 queen street", outsideNumber112, 8)))
                .containsExactly(
                        "12 Queen Street, Papakura",
                        "112 Queen Street, Auckland Central, Auckland");
    }

    @Test
    void keepsTheHousesOfOneStreetInOrderRatherThanSortingThemByAFewMetres() {
        insertAddress(40, "15/286 Mount Wellington Highway, Mount Wellington", -36.9172, 174.8412);

        // 35 metres apart, and the search is looking from the door of the second one.
        ReferencePoint outsideUnit15 = new ReferencePoint(-36.9172, 174.8412);

        assertThat(names(placeSearch.search("mount wellington highway", outsideUnit15, 8)))
                .containsExactly(
                        "2/286 Mount Wellington Highway, Mount Wellington",
                        "15/286 Mount Wellington Highway, Mount Wellington");
    }

    @Test
    void doesNotLetAPlaceThatBarelyMatchesNearbyOvertakeAFullMatchFarAway() {
        // Found because its address has both words in it. Its name has neither.
        insertPoi("st", "Southland Travel", "2 Newcastle St, World Trade Centre", null, "Invercargill",
                0.99, -46.4130, 168.3540);

        assertThat(names(placeSearch.search("new world", INVERCARGILL, 8)))
                .containsExactly("New World Remuera", "Southland Travel");
    }

    @Test
    void ranksTheNearestCandidatesWhenThereAreTooManyToRankThemAll() {
        PlaceSearch capped = new PostgresPlaceSearch(jdbc, new SearchProperties(0.3, 1, 100));

        // Two addresses and three places mention Newmarket. One of each gets through.
        ReferencePoint at277Broadway = new ReferencePoint(-36.8690, 174.7780);
        assertThat(names(capped.search("newmarket", at277Broadway, 8)))
                .containsExactlyInAnyOrder("277 Broadway, Newmarket, Auckland", "H&M");

        ReferencePoint atTheWarehouse = new ReferencePoint(-36.8720, 174.7770);
        assertThat(names(capped.search("newmarket", atTheWarehouse, 8)))
                .containsExactlyInAnyOrder("100 Broadway, Newmarket", "The Warehouse");
    }

    @Test
    void neverLooksAtFewerMatchesThanItRanks() {
        // A scan limit under the candidate limit would otherwise quietly become the cap.
        PlaceSearch misconfigured = new PostgresPlaceSearch(jdbc, new SearchProperties(0.3, 2, 1));

        assertThat(misconfigured.search("newmarket", REMUERA, 8)).hasSize(4);
    }

    private static List<String> names(List<FoundPlace> found) {
        return found.stream().map(FoundPlace::name).toList();
    }

    private void insertAddress(long id, String fullAddress, double latitude, double longitude) {
        jdbc.sql("INSERT INTO address (id, full_address, latitude, longitude) VALUES (?, ?, ?, ?)")
                .params(id, fullAddress, latitude, longitude)
                .update();
    }

    private void insertPoi(String id, String name, String address, String suburb, String locality,
                           double confidence, double latitude, double longitude) {
        jdbc.sql("""
                        INSERT INTO poi (id, source, name, address, suburb, locality, confidence,
                                         latitude, longitude)
                        VALUES (?, 'test', ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, name, address, suburb, locality, confidence, latitude, longitude)
                .update();
    }
}
