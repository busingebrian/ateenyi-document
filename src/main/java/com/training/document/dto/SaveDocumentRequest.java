package com.training.document.dto;

import com.training.document.entity.DocStatus;
import com.training.document.entity.DocType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SaveDocumentRequest {

    @NotNull(message = "docType is required")
    @Size(max = 2)
    private DocType docType;

    @NotNull(message = "status is required")
    private DocStatus status;

    @NotBlank(message = "content is required")
    @Size(max = 2000, message = "Length exceeded")
    private String content;

    @Size(max = 20)
    private String tin;

    private String docCode;

    private String viewId;
}
