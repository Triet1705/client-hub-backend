package com.clienthub.domain.repository;

import com.clienthub.domain.entity.User;
import com.clienthub.domain.enums.Role;
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

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.tenantId = :tenantId")
    long countByRole(@Param("role") Role role, @Param("tenantId") String tenantId);

    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId")
    Page<User> findAllByTenantId(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.tenantId = :tenantId")
    Optional<User> findByEmailCustom(@Param("email") String email, @Param("tenantId") String tenantId);

    Optional<User> findByEmail(String email);

    @Query(value = "SELECT * FROM users WHERE email = ?1", nativeQuery = true)
    Optional<User> findByEmailIgnoringTenant(String email);

    boolean existsByEmail(String email);

    @Query(value = "SELECT * FROM users", nativeQuery = true)
    List<User> findAllNative();
}
