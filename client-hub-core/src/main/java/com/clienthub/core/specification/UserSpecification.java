package com.clienthub.core.specification;

import com.clienthub.core.dto.UserSearchRequest;
import com.clienthub.core.domain.entity.User;
import com.clienthub.core.domain.enums.Role;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class UserSpecification {
    public static Specification<User> searchUsers(UserSearchRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                String keyword = "%" + request.getKeyword().toLowerCase() + "%";
                predicates.add(
                    criteriaBuilder.or(
                        criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("email")), 
                            keyword
                        ),
                        criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("fullName")), 
                            keyword
                        )
                    )
                );
            }

            if (request.getRole() != null && !request.getRole().trim().isEmpty()) {
                try {
                    Role role = Role.valueOf(request.getRole().toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("role"), role));
                } catch (IllegalArgumentException e) {
                    // Invalid role, ignore filter
                }
            }

            if (request.getActive() != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), request.getActive()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
