package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.SavedQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedQueryRepository extends JpaRepository<SavedQuery, Long> {

    /** A user's own queries, plus any another administrator chose to share. */
    @Query("""
            SELECT q FROM SavedQuery q
            WHERE q.ownerUsername = :username OR q.shared = true
            ORDER BY q.queryName
            """)
    List<SavedQuery> findVisibleTo(@Param("username") String username);

    Optional<SavedQuery> findByOwnerUsernameAndQueryName(String ownerUsername, String queryName);
}
