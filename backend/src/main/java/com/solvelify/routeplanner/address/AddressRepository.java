package com.solvelify.routeplanner.address;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AddressRepository extends JpaRepository<AddressEntity, Long> {

    /**
     * Finds addresses containing the typed words, best match first.
     *
     * <p>Native SQL, because the two things that make this work belong to PostgreSQL rather
     * than JPA. The trigram index created in the migration keeps a leading-wildcard ILIKE fast
     * over 2.4 million rows, and `similarity` ranks what comes back.
     *
     * <p>Each space in the search becomes a wildcard, so "bassett road remuera" is matched as
     * `%bassett%road%remuera%`. A plain substring search fails here: the stored address reads
     * "20A Bassett Road, Remuera", and the comma alone is enough to stop it matching.
     *
     * <p>The trade-off is that words must be typed in the order they appear, which is how
     * people type addresses. Shorter addresses win ties, so a street beats a long unit address
     * on the same street.
     */
    @Query(value = """
            SELECT *
            FROM address
            WHERE full_address ILIKE '%' || replace(:term, ' ', '%') || '%'
            ORDER BY similarity(full_address, :term) DESC, length(full_address) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<AddressEntity> search(@Param("term") String term, @Param("limit") int limit);
}
