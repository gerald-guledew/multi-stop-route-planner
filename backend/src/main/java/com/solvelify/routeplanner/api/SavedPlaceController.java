package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.saved.SavedPlace;
import com.solvelify.routeplanner.saved.SavedPlaces;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The places somebody saved so they can pick them again without typing.
 *
 * <p>There is one list and no notion of whose it is, because the application has no users.
 * Whoever can reach it can read the list, and the list is likely to hold a home address.
 */
@RestController
@RequestMapping("/api/v1/saved-places")
public class SavedPlaceController {

    private final SavedPlaces savedPlaces;

    public SavedPlaceController(SavedPlaces savedPlaces) {
        this.savedPlaces = savedPlaces;
    }

    /**
     * Marked not to be stored, unlike search results. These are private, and a copy kept by a
     * browser or by anything in between would outlive a place that has since been removed.
     */
    @GetMapping
    public ResponseEntity<List<SavedPlaceResponse>> list() {
        List<SavedPlaceResponse> places = savedPlaces.all().stream()
                .map(SavedPlaceResponse::from)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(places);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedPlaceResponse save(@Valid @RequestBody SavePlaceRequest request) {
        return SavedPlaceResponse.from(
                savedPlaces.save(request.name(), request.latitude(), request.longitude()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id) {
        savedPlaces.remove(id);
    }

    /** @param id what to remove it by. Handed out when the place is saved */
    public record SavedPlaceResponse(long id, String name, double latitude, double longitude) {

        static SavedPlaceResponse from(SavedPlace place) {
            return new SavedPlaceResponse(place.getId(), place.getName(), place.getLatitude(), place.getLongitude());
        }
    }
}
