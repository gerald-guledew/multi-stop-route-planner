package com.solvelify.routeplanner.saved;

/** Asked to remove a saved place that is not there, or not there any more. It becomes a 404. */
public class SavedPlaceNotFoundException extends RuntimeException {

    public SavedPlaceNotFoundException(long id) {
        super("No saved place has the id " + id + ".");
    }
}
