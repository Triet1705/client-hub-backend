package com.clienthub.application.mapper;

import com.clienthub.domain.entity.Comment;
import com.clienthub.application.dto.communication.CommentRequest;
import com.clienthub.application.dto.communication.CommentResponse;

public interface CommentMapper {

    CommentResponse toResponse(Comment comment);

    Comment toEntity(CommentRequest request);
}