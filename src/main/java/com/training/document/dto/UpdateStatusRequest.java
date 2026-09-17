package com.training.document.dto;

import com.training.document.entity.DocStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@ToString
public class UpdateStatusRequest {

    @NotNull(message = "id is required")
    private UUID id;

    @NotNull(message = "status is required")
    private DocStatus status;
}
