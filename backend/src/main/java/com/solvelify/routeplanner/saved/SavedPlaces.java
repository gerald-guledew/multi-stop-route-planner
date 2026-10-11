package com.solvelify.routeplanner.saved;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The places somebody has saved: listing them, saving another, removing one.
 */
@Service
public class SavedPlaces {

    /** The index that allows each name once, whatever the capitals, as migration V1 named it. */
    private static final String ONE_NAME_ONCE = "saved_place_name_key";

    private final SavedPlaceRepository repository;

    public SavedPlaces(SavedPlaceRepository repository) {
        this.repository = repository;
    }

    /**
     * Every saved place, in the order a person would look for one: by name, with capitals and
     * macrons counting for nothing until two names are otherwise the same.
     *
     * <p>Sorted here and not by the database, because a database orders text by whatever rules
     * it was created with, and those differ from one machine to the next.
     */
    public List<SavedPlace> all() {
        Collator asAPersonWould = Collator.getInstance(Locale.ROOT);
        return repository.findAll().stream()
                .sorted(Comparator.comparing(SavedPlace::getName, asAPersonWould))
                .toList();
    }

    /**
     * @throws PlaceNameTakenException when a place is already saved under that name
     */
    public SavedPlace save(String name, double latitude, double longitude) {
        String tidyName = name.strip();
        try {
            // Flushed at once, so a name that is taken is refused here and not at some later
            // point that nobody is watching.
            return repository.saveAndFlush(new SavedPlace(tidyName, latitude, longitude));
        } catch (DataIntegrityViolationException refusedByTheDatabase) {
            if (isAboutTheName(refusedByTheDatabase)) {
                throw new PlaceNameTakenException(tidyName);
            }
            throw refusedByTheDatabase;
        }
    }

    /**
     * The table is what keeps names apart, not a look beforehand: two requests arriving
     * together would both look, both see the name free, and both save. So the question here is
     * only which of the table's rules said no.
     */
    private static boolean isAboutTheName(DataIntegrityViolationException refused) {
        return refused.getCause() instanceof ConstraintViolationException brokenRule
                && ONE_NAME_ONCE.equals(brokenRule.getConstraintName());
    }

    /**
     * @throws SavedPlaceNotFoundException when there is no such place
     */
    public void remove(long id) {
        if (!repository.existsById(id)) {
            throw new SavedPlaceNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
