package com.clienthub.core.mapper;

import com.clienthub.core.domain.entity.Comment;
import com.clienthub.core.domain.entity.User;
import com.clienthub.core.dto.UserSummaryDto;
import com.clienthub.core.dto.communication.CommentRequest;
import com.clienthub.core.dto.communication.CommentResponse;
import org.springframework.stereotype.Component;

@Component
public class CommentMapperImpl implements CommentMapper {

    @Override
    public CommentResponse toResponse(Comment comment) {
        if (comment == null) {
            return null;
        }

        CommentResponse response = new CommentResponse();
        response.setId(comment.getId());
        response.setContent(comment.getContent());
        response.setDeleted(comment.isDeleted());
        response.setCreatedAt(comment.getCreatedAt());
        response.setUpdatedAt(comment.getUpdatedAt());

        if (comment.getThread() != null) {
            response.setThreadId(comment.getThread().getId());
        }

        if (comment.getAuthor() != null) {
            response.setAuthor(mapUserToSummary(comment.getAuthor()));
        }

        return response;
    }

    @Override
    public Comment toEntity(CommentRequest request) {
        if (request == null) {
            return null;
        }

        Comment comment = new Comment();
        comment.setContent(request.getContent());

        return comment;
    }

    private UserSummaryDto mapUserToSummary(User user) {
        if (user == null) {
            return null;
        }
        return new UserSummaryDto(
                user.getId(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getTenantId(),
                user.isActive() ? "ACTIVE" : "INACTIVE",
                0,
                user.getLastLoginAt()
        );
    }
}