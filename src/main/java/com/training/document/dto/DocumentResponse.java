package com.training.document.dto;

import com.training.document.entity.DocStatus;
import com.training.document.entity.DocType;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@ToString
@Builder
public class DocumentResponse {

    private UUID id;
    private String drn;
    private DocType docType;
    private DocStatus status;
    private String content;
    private String tin;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
