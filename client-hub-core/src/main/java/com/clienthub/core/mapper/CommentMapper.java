package com.clienthub.core.mapper;

import com.clienthub.core.domain.entity.Comment;
import com.clienthub.core.dto.communication.CommentRequest;
import com.clienthub.core.dto.communication.CommentResponse;

public interface CommentMapper {

    CommentResponse toResponse(Comment comment);

    Comment toEntity(CommentRequest request);
}