package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.search.ReferencePoint;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Reads the {@code near} parameter, written as {@code latitude,longitude}.
 *
 * <p>Spring Boot hands every {@link Converter} bean to Spring MVC, so declaring this one is
 * all it takes for a controller method to ask for a {@link ReferencePoint}. When it throws,
 * the caller gets a 400 that names the parameter.
 */
@Component
class ReferencePointConverter implements Converter<String, ReferencePoint> {

    @Override
    public ReferencePoint convert(String text) {
        // "near=" with nothing after it means the same as leaving it out.
        if (text.isBlank()) {
            return null;
        }

        String[] parts = text.split(",", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("near must be latitude,longitude, was " + text);
        }

        return new ReferencePoint(
                Double.parseDouble(parts[0].strip()),
                Double.parseDouble(parts[1].strip()));
    }
}
