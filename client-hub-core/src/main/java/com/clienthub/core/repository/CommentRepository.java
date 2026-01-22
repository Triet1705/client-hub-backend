package com.clienthub.core.repository;
import com.clienthub.core.domain.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT c FROM Comment c WHERE c.thread.id = :threadId AND c.tenantId = :tenantId ORDER BY c.createdAt ASC")
    Page<Comment> findByThreadIdAndTenantId(
            @Param("threadId") Long threadId,
            @Param("tenantId") String tenantId,
            Pageable pageable
    );
}
