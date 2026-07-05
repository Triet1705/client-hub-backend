package com.clienthub.domain.repository;
import com.clienthub.domain.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = {"author", "thread"})
    @Query("SELECT c FROM Comment c WHERE c.thread.id = :threadId AND c.tenantId = :tenantId ORDER BY c.createdAt ASC")
    Page<Comment> findByThreadIdAndTenantId(
            @Param("threadId") Long threadId,
            @Param("tenantId") String tenantId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"author", "thread"})
    Optional<Comment> findById(Long id);

    @Query("""
            SELECT c FROM Comment c
            JOIN FETCH c.author
            JOIN FETCH c.thread t
            WHERE c.tenantId = :tenantId
              AND t.tenantId = :tenantId
              AND (
                (t.targetType = com.clienthub.domain.enums.CommentTargetType.PROJECT AND t.targetId = :projectId)
                OR (t.targetType = com.clienthub.domain.enums.CommentTargetType.TASK AND t.targetId IN :taskIds)
                OR (t.targetType = com.clienthub.domain.enums.CommentTargetType.INVOICE AND t.targetId IN :invoiceIds)
              )
            ORDER BY c.createdAt DESC
            """)
    List<Comment> findProjectScopedComments(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("taskIds") Collection<String> taskIds,
            @Param("invoiceIds") Collection<String> invoiceIds
    );
}
