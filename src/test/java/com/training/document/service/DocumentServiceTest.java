package com.training.document.service;

import com.training.document.dto.DocumentResponse;
import com.training.document.dto.SaveDocumentRequest;
import com.training.document.dto.SearchDto;
import com.training.document.dto.UpdateStatusRequest;
import com.training.document.entity.DocStatus;
import com.training.document.entity.DocType;
import com.training.document.entity.Document;
import com.training.document.exception.DocumentNotFoundException;
import com.training.document.repository.DocumentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DocumentServiceTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void save_generatesDrnAndSetsCreatedAtEqualToModifiedAt() {
        DocumentResponse response = documentService.save(saveRequest(DocType.rt, DocStatus.SUBMITTED, "hello world"));

        assertThat(response.getId()).isNotNull();
        assertThat(response.getDrn()).matches("rt-[A-Z0-9]{6}");
        assertThat(response.getStatus()).isEqualTo(DocStatus.SUBMITTED);
        assertThat(response.getContent()).isEqualTo("hello world");
        assertThat(response.getCreatedAt()).isEqualTo(response.getModifiedAt());
    }

    @Test
    void save_generatesDifferentDrnPrefixPerDocType() {
        DocumentResponse rt = documentService.save(saveRequest(DocType.rt, DocStatus.SUBMITTED, "a"));
        DocumentResponse tp = documentService.save(saveRequest(DocType.tp, DocStatus.SUBMITTED, "b"));

        assertThat(rt.getDrn()).startsWith("rt-");
        assertThat(tp.getDrn()).startsWith("tp-");
    }

    @Test
    void updateStatus_changesStatusAndBumpsModifiedAtButKeepsCreatedAt() throws InterruptedException {
        DocumentResponse saved = documentService.save(saveRequest(DocType.rt, DocStatus.SUBMITTED, "content"));
        Thread.sleep(5);

        DocumentResponse updated = documentService.updateStatus(statusRequest(saved.getId(), DocStatus.APPROVED));

        assertThat(updated.getStatus()).isEqualTo(DocStatus.APPROVED);
        assertThat(updated.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(updated.getModifiedAt()).isAfter(saved.getModifiedAt());
    }

    @Test
    void updateStatus_unknownId_throwsNotFound() {
        UpdateStatusRequest request = statusRequest(UUID.randomUUID(), DocStatus.APPROVED);

        assertThatThrownBy(() -> documentService.updateStatus(request))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void getById_unknownId_throwsNotFound() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> documentService.getById(id))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void getByDrn_returnsMatchingDocument() {
        DocumentResponse saved = documentService.save(saveRequest(DocType.tp, DocStatus.SUBMITTED, "content"));

        DocumentResponse found = documentService.getByDrn(saved.getDrn());

        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    @Test
    void getByDrn_unknownDrn_throwsNotFound() {
        assertThatThrownBy(() -> documentService.getByDrn("rt-000000"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void searchByDrn_ordersByModifiedAtDescThenByDocTypeAsc() {
        LocalDateTime now = LocalDateTime.now();
        insertDocument("rt-search1", "rt", "SUBMITTED", now.minusMinutes(10));
        insertDocument("rt-search2", "rt", "SUBMITTED", now.minusMinutes(5));
        insertDocument("tp-search3", "tp", "SUBMITTED", now.minusMinutes(5));

        Page<DocumentResponse> results = documentService.searchByDrn(search("search", null, null, 0, 20));

        assertThat(results.getTotalElements()).isEqualTo(3);
        assertThat(results.getContent()).extracting(DocumentResponse::getDrn)
                .containsExactly("rt-search2", "tp-search3", "rt-search1");
    }

    @Test
    void searchByDrn_pagesResults() {
        LocalDateTime now = LocalDateTime.now();
        insertDocument("rt-page1", "rt", "SUBMITTED", now.minusMinutes(10));
        insertDocument("rt-page2", "rt", "SUBMITTED", now.minusMinutes(5));
        insertDocument("tp-page3", "tp", "SUBMITTED", now.minusMinutes(1));

        Page<DocumentResponse> firstPage = documentService.searchByDrn(search("page", null, null, 0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).extracting(DocumentResponse::getDrn)
                .containsExactly("tp-page3", "rt-page2");
    }

    @Test
    void searchByDrn_noMatches_returnsEmptyPage() {
        assertThat(documentService.searchByDrn(search("does-not-exist", null, null, 0, 20)).getContent()).isEmpty();
    }

    @Test
    void save_persistsDocCodeAndViewId() {
        SaveDocumentRequest request = saveRequest(DocType.rt, DocStatus.SUBMITTED, "codes");
        request.setDocCode("DC-1");
        request.setViewId("view-1");

        DocumentResponse saved = documentService.save(request);

        assertThat(saved.getDocCode()).isEqualTo("DC-1");
        assertThat(saved.getViewId()).isEqualTo("view-1");
        DocumentResponse fetched = documentService.getById(saved.getId());
        assertThat(fetched.getDocCode()).isEqualTo("DC-1");
        assertThat(fetched.getViewId()).isEqualTo("view-1");
    }

    @Test
    void searchByDrn_filtersByDocCodeAndViewId() {
        SaveDocumentRequest first = saveRequest(DocType.rt, DocStatus.SUBMITTED, "a");
        first.setDocCode("DC-A");
        first.setViewId("view-1");
        SaveDocumentRequest second = saveRequest(DocType.rt, DocStatus.SUBMITTED, "b");
        second.setDocCode("DC-B");
        second.setViewId("view-1");
        DocumentResponse a = documentService.save(first);
        DocumentResponse b = documentService.save(second);

        assertThat(documentService.searchByDrn(search("rt-", "DC-A", null, 0, 20)).getContent())
                .extracting(DocumentResponse::getId).containsExactly(a.getId());
        assertThat(documentService.searchByDrn(search("rt-", null, "view-1", 0, 20)).getContent())
                .extracting(DocumentResponse::getId).containsExactlyInAnyOrder(a.getId(), b.getId());
        assertThat(documentService.searchByDrn(search("rt-", "DC-B", "view-1", 0, 20)).getContent())
                .extracting(DocumentResponse::getId).containsExactly(b.getId());
        assertThat(documentService.searchByDrn(search("rt-", "DC-A", "view-2", 0, 20)).getContent())
                .isEmpty();
    }

    private void insertDocument(String drn, String docType, String status, LocalDateTime modifiedAt) {
        Document document = new Document();
        document.setDrn(drn);
        document.setDocType(DocType.valueOf(docType));
        document.setStatus(DocStatus.valueOf(status));
        document.setContent("content");
        documentRepository.saveAndFlush(document);

        // @PrePersist always stamps now(); backdate via a JPQL bulk update, which bypasses
        // entity lifecycle callbacks, then clear the persistence context so later reads see it.
        entityManager.createQuery("UPDATE Document d SET d.createdAt = :ts, d.modifiedAt = :ts WHERE d.id = :id")
                .setParameter("ts", modifiedAt)
                .setParameter("id", document.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private SaveDocumentRequest saveRequest(DocType docType, DocStatus status, String content) {
        SaveDocumentRequest request = new SaveDocumentRequest();
        request.setDocType(docType);
        request.setStatus(status);
        request.setContent(content);
        return request;
    }

    private SearchDto search(String drn, String docCode, String viewId, int page, int size) {
        SearchDto searchDto = new SearchDto();
        searchDto.setDrn(drn);
        searchDto.setDocCode(docCode);
        searchDto.setViewId(viewId);
        searchDto.setPage(page);
        searchDto.setSize(size);
        return searchDto;
    }

    private UpdateStatusRequest statusRequest(UUID id, DocStatus status) {
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setId(id);
        request.setStatus(status);
        return request;
    }
}
