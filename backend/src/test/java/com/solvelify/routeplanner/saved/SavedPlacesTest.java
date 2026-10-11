package com.solvelify.routeplanner.saved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Saved places against a real PostgreSQL, because two of the rules are the table's own: one
 * name once whatever the capitals, and an id the database hands out.
 *
 * <p>Not wrapped in a transaction that rolls back, unlike the search tests. A refused insert
 * leaves a PostgreSQL transaction unusable, and the tests go on to look at what is left. So
 * each test starts by emptying the table. Only these tests write to it.
 */
@SpringBootTest
class SavedPlacesTest {

    @Autowired
    private SavedPlaces savedPlaces;

    @Autowired
    private SavedPlaceRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTheTable() {
        repository.deleteAllInBatch();
    }

    @Test
    void keepsWhatWasSaved() {
        SavedPlace saved = savedPlaces.save("Home", -36.8689416667, 174.7832303333);

        assertThat(saved.getId()).isNotNull();
        assertThat(savedPlaces.all()).singleElement().satisfies(place -> {
            assertThat(place.getId()).isEqualTo(saved.getId());
            assertThat(place.getName()).isEqualTo("Home");
            // To the last digit. The map screen tells a saved place from a pin by its position.
            assertThat(place.getLatitude()).isEqualTo(-36.8689416667);
            assertThat(place.getLongitude()).isEqualTo(174.7832303333);
        });
    }

    @Test
    void theDatabaseNotesWhenAPlaceWasSaved() {
        SavedPlace saved = savedPlaces.save("Home", -36.8689, 174.7832);

        OffsetDateTime createdAt = jdbc.sql("SELECT created_at FROM saved_place WHERE id = :id")
                .param("id", saved.getId())
                .query(OffsetDateTime.class)
                .single();
        assertThat(createdAt).isNotNull();
    }

    @Test
    void listsPlacesByNameWhateverTheCapitalsOrMacrons() {
        savedPlaces.save("cherry", -36.90, 174.80);
        savedPlaces.save("Zoo", -36.86, 174.72);
        savedPlaces.save("Banana", -36.91, 174.81);
        savedPlaces.save("Ōrākei shops", -36.85, 174.81);
        savedPlaces.save("apple", -36.92, 174.82);

        assertThat(names(savedPlaces.all())).containsExactly("apple", "Banana", "cherry", "Ōrākei shops", "Zoo");
    }

    @Test
    void trimsTheSpacesAroundAName() {
        savedPlaces.save("  Home \t", -36.8689, 174.7832);

        assertThat(names(savedPlaces.all())).containsExactly("Home");
    }

    @Test
    void refusesANameThatIsTaken() {
        savedPlaces.save("Home", -36.8689, 174.7832);

        assertThatThrownBy(() -> savedPlaces.save("Home", -36.9000, 174.8000))
                .isInstanceOf(PlaceNameTakenException.class)
                .hasMessageContaining("\"Home\"");
    }

    @Test
    void refusesATakenNameWhateverTheCapitalsOrSpaces() {
        savedPlaces.save("Home", -36.8689, 174.7832);

        assertThatThrownBy(() -> savedPlaces.save(" HOME ", -36.9000, 174.8000))
                .isInstanceOf(PlaceNameTakenException.class)
                .extracting(refused -> ((PlaceNameTakenException) refused).name())
                .isEqualTo("HOME");
    }

    @Test
    void keepsTheFirstPlaceWhenASecondOfThatNameIsRefused() {
        savedPlaces.save("Home", -36.8689, 174.7832);

        assertThatThrownBy(() -> savedPlaces.save("home", -36.9000, 174.8000))
                .isInstanceOf(PlaceNameTakenException.class);

        assertThat(savedPlaces.all()).singleElement().satisfies(place -> {
            assertThat(place.getName()).isEqualTo("Home");
            assertThat(place.getLatitude()).isEqualTo(-36.8689);
        });
    }

    @Test
    void letsOneSpotBeSavedUnderTwoNames() {
        savedPlaces.save("The office", -36.8485, 174.7621);
        savedPlaces.save("Work", -36.8485, 174.7621);

        assertThat(names(savedPlaces.all())).containsExactly("The office", "Work");
    }

    @Test
    void removesAPlace() {
        SavedPlace home = savedPlaces.save("Home", -36.8689, 174.7832);
        savedPlaces.save("The office", -36.8485, 174.7621);

        savedPlaces.remove(home.getId());

        assertThat(names(savedPlaces.all())).containsExactly("The office");
    }

    @Test
    void freesTheNameOfARemovedPlace() {
        SavedPlace home = savedPlaces.save("Home", -36.8689, 174.7832);
        savedPlaces.remove(home.getId());

        SavedPlace again = savedPlaces.save("Home", -36.9000, 174.8000);

        assertThat(again.getId()).isNotEqualTo(home.getId());
        assertThat(savedPlaces.all()).singleElement()
                .satisfies(place -> assertThat(place.getLatitude()).isEqualTo(-36.9000));
    }

    @Test
    void saysSoWhenThereIsNoSuchPlaceToRemove() {
        SavedPlace home = savedPlaces.save("Home", -36.8689, 174.7832);
        long noSuchId = home.getId() + 1000;

        assertThatThrownBy(() -> savedPlaces.remove(noSuchId))
                .isInstanceOf(SavedPlaceNotFoundException.class)
                .hasMessageContaining(String.valueOf(noSuchId));
        assertThat(savedPlaces.all()).hasSize(1);
    }

    private static List<String> names(List<SavedPlace> places) {
        return places.stream().map(SavedPlace::getName).toList();
    }
}
