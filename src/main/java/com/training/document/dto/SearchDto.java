package com.training.document.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SearchDto {

    @NotNull(message = "drn is required")
    private String drn;

    private String docCode;

    private String viewId;

    @Min(value = 0, message = "page must not be negative")
    private int page = 0;

    @Min(value = 1, message = "size must be at least 1")
    private int size = 10;
}
