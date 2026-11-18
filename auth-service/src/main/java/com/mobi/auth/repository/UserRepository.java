package com.mobi.auth.repository;

import com.mobi.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsername(String username);

    // Both columns are searched so an administrator can paste in whichever identifier
    // they happen to have. Paged at the database, never in memory.
    java.util.Optional<User> findByEmail(String email);

    java.util.List<User> findByRoleIn(java.util.Collection<String> roles);

    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String username, String email, Pageable pageable);
}
