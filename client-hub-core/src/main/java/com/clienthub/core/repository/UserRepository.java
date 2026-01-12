package com.clienthub.core.repository;

import com.clienthub.core.domain.entity.User;
import com.clienthub.core.domain.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, 
                                        JpaSpecificationExecutor<User> {

    long countByRole(Role role);

    Page<User> findAll(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailCustom(@Param("email") String email);

    Optional<User> findByEmail(String email);

    @Query(value = "SELECT * FROM users WHERE email = ?1", nativeQuery = true)
    Optional<User> findByEmailIgnoringTenant(String email);

    boolean existsByEmail(String email);

    @Query(value = "SELECT * FROM users", nativeQuery = true)
    List<User> findAllNative();
}
