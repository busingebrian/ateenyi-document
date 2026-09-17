package com.training.document.repository;

import com.training.document.entity.Document;
import org.springframework.data.jpa.domain.Specification;

public final class DocumentSpecifications {

    private DocumentSpecifications() {
    }

    public static Specification<Document> drnContains(String drn) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("drn")), "%" + drn.toLowerCase() + "%");
    }
}
