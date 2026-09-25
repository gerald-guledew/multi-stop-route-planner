package com.solvelify.routeplanner.planning;

/**
 * A place that cannot be reached by road.
 *
 * <p>Usually a coordinate dropped away from any street: the middle of an airfield, a park, a
 * shopping centre roof. The request is perfectly well formed, so this is not a validation error,
 * and it is not the server's fault either. It becomes a 422 with the offending place named.
 */
public class UnroutablePlaceException extends RuntimeException {

    private final String placeName;

    public UnroutablePlaceException(String placeName, String reason) {
        super("Cannot route to " + placeName + ". " + reason);
        this.placeName = placeName;
    }

    public String placeName() {
        return placeName;
    }
}
