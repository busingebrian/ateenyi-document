package com.training.document.controller;

import com.training.document.dto.DocumentResponse;
import com.training.document.dto.SaveDocumentRequest;
import com.training.document.dto.SearchDto;
import com.training.document.dto.UpdateStatusRequest;
import com.training.document.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse save(@Valid @RequestBody SaveDocumentRequest request) {
        return documentService.save(request);
    }

    @PatchMapping("/status")
    public DocumentResponse updateStatus(@Valid @RequestBody UpdateStatusRequest request) {
        return documentService.updateStatus(request);
    }

    @GetMapping("/{id}")
    public DocumentResponse getById(@PathVariable UUID id) {
        return documentService.getById(id);
    }

    @GetMapping("/drn/{drn}")
    public DocumentResponse getByDrn(@PathVariable String drn) {
        return documentService.getByDrn(drn);
    }

    @GetMapping("/search")
    public Page<DocumentResponse> search(@Valid @ModelAttribute SearchDto searchDto) {
        return documentService.searchByDrn(searchDto);
    }

    @PostMapping("/search")
    public Page<DocumentResponse> searchByBody(@Valid @RequestBody SearchDto searchDto) {
        return documentService.searchByDrn(searchDto);
    }
}
