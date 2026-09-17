package com.training.document.service;

import com.training.document.dto.DocumentResponse;
import com.training.document.dto.SaveDocumentRequest;
import com.training.document.dto.UpdateStatusRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DocumentService {

    DocumentResponse save(SaveDocumentRequest request);

    DocumentResponse updateStatus(UpdateStatusRequest request);

    DocumentResponse getById(UUID id);

    DocumentResponse getByDrn(String drn);

    Page<DocumentResponse> searchByDrn(String drn, Pageable pageable);
}
