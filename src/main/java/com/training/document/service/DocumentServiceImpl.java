package com.training.document.service;

import com.training.document.dto.DocumentMapper;
import com.training.document.dto.DocumentResponse;
import com.training.document.dto.SaveDocumentRequest;
import com.training.document.dto.UpdateStatusRequest;
import com.training.document.entity.Document;
import com.training.document.exception.DocumentNotFoundException;
import com.training.document.notification.NotificationMessage;
import com.training.document.notification.NotificationProperties;
import com.training.document.notification.NotificationSender;
import com.training.document.repository.DocumentRepository;
import com.training.document.repository.DocumentSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentServiceImpl implements DocumentService {

    private static final Sort SEARCH_SORT = Sort.by(Sort.Order.desc("modifiedAt"), Sort.Order.asc("docType"));

    private final DocumentRepository documentRepository;
    private final DrnGenerator drnGenerator;
    private final NotificationSender notificationSender;
    private final NotificationProperties notificationProperties;

    @Override
    @Transactional
    public DocumentResponse save(SaveDocumentRequest request) {
        Document document = new Document();
        document.setDrn(drnGenerator.generate(request.getDocType()));
        document.setDocType(request.getDocType());
        document.setStatus(request.getStatus());
        document.setContent(request.getContent());

        Document saved = documentRepository.save(document);
        log.info("Document saved: id={}, drn={}, status={}", saved.getId(), saved.getDrn(), saved.getStatus());
        notificationSender.send(NotificationMessage.forStatus(saved.getDrn(), saved.getStatus(), notificationProperties.getEmail()));
        return DocumentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DocumentResponse updateStatus(UpdateStatusRequest request) {
        Document document = documentRepository.findById(request.getId())
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: id=" + request.getId()));

        var previousStatus = document.getStatus();
        document.setStatus(request.getStatus());
        Document updated = documentRepository.saveAndFlush(document);
        log.info("Document status updated: id={}, drn={}, {} -> {}", updated.getId(), updated.getDrn(),
                previousStatus, updated.getStatus());
        notificationSender.send(NotificationMessage.forStatus(updated.getDrn(), updated.getStatus(), notificationProperties.getEmail()));
        return DocumentMapper.toResponse(updated);
    }

    @Override
    public DocumentResponse getById(UUID id) {
        return documentRepository.findById(id)
                .map(DocumentMapper::toResponse)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: id=" + id));
    }

    @Override
    public DocumentResponse getByDrn(String drn) {
        return documentRepository.findByDrn(drn)
                .map(DocumentMapper::toResponse)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: drn=" + drn));
    }

    @Override
    public Page<DocumentResponse> searchByDrn(String drn, Pageable pageable) {
        Specification<Document> spec = DocumentSpecifications.drnContains(drn);
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), SEARCH_SORT);
        return documentRepository.findAll(spec, sortedPageable).map(DocumentMapper::toResponse);
    }
}
