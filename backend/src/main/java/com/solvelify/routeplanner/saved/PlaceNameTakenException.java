package com.solvelify.routeplanner.saved;

/**
 * Two saved places cannot share a name, whatever the capitals: a list with "Home" in it twice
 * leaves nobody sure which one they are picking. It becomes a 409 with the name in it.
 */
public class PlaceNameTakenException extends RuntimeException {

    private final String name;

    public PlaceNameTakenException(String name) {
        super("A place called \"" + name + "\" is already saved."
                + " Remove it first, or save this one under another name.");
        this.name = name;
    }

    public String name() {
        return name;
    }
}
