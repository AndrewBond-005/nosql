package ru.library.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.library.event.model.Event;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, String> {
    List<Event> findByCategory(String category);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Event e set e.availableCopies = e.availableCopies - 1, e.updatedAt = current_timestamp where e.id = :eventId and e.availableCopies > 0")
    int reserveOneCopy(@Param("eventId") String eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Event e set e.availableCopies = e.availableCopies + 1, e.updatedAt = current_timestamp where e.id = :eventId")
    int releaseOneCopy(@Param("eventId") String eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Event e set e.viewCount = e.viewCount + :delta where e.id = :eventId")
    int incrementViewCount(@Param("eventId") String eventId, @Param("delta") long delta);
}
