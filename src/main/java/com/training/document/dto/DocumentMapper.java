package com.training.document.dto;

import com.training.document.entity.Document;

public final class DocumentMapper {

    private DocumentMapper() {
    }

    public static DocumentResponse toResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .drn(document.getDrn())
                .docType(document.getDocType())
                .status(document.getStatus())
                .content(document.getContent())
                .tin(document.getTin())
                .createdAt(document.getCreatedAt())
                .modifiedAt(document.getModifiedAt())
                .build();
    }
}
