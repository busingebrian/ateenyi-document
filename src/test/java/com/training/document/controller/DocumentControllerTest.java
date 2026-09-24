package com.training.document.controller;

import com.training.document.dto.SaveDocumentRequest;
import com.training.document.dto.UpdateStatusRequest;
import com.training.document.entity.DocStatus;
import com.training.document.entity.DocType;
import com.training.document.entity.Document;
import com.training.document.repository.DocumentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void save_validRequest_returns201WithGeneratedDrn() throws Exception {
        SaveDocumentRequest request = saveRequest(DocType.rt, DocStatus.SUBMITTED, "hello");

        mockMvc.perform(post("/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.drn").value(org.hamcrest.Matchers.matchesPattern("rt-[A-Z0-9]{6}")))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.docType").value("rt"));
    }

    @Test
    void save_missingMandatoryFields_returns400WithAllErrors() throws Exception {
        SaveDocumentRequest request = new SaveDocumentRequest();

        mockMvc.perform(post("/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].code").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "invalid.docType", "invalid.status", "invalid.content")));
    }

    @Test
    void save_contentTooLong_returns400() throws Exception {
        SaveDocumentRequest request = saveRequest(DocType.rt, DocStatus.SUBMITTED, "a".repeat(2001));

        mockMvc.perform(post("/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("invalid.content"))
                .andExpect(jsonPath("$.errors[0].message").value("Length exceeded"));
    }

    @Test
    void updateStatus_missingFields_returns400WithAllErrors() throws Exception {
        UpdateStatusRequest request = new UpdateStatusRequest();

        mockMvc.perform(patch("/document/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].code").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "invalid.id", "invalid.status")));
    }

    @Test
    void updateStatus_existingDocument_updatesAndReturnsIt() throws Exception {
        String createResponse = mockMvc.perform(post("/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveRequest(DocType.rt, DocStatus.SUBMITTED, "hello"))))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(createResponse).get("id").asString());

        UpdateStatusRequest updateRequest = new UpdateStatusRequest();
        updateRequest.setId(id);
        updateRequest.setStatus(DocStatus.APPROVED);

        mockMvc.perform(patch("/document/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void updateStatus_unknownId_returns404() throws Exception {
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setId(UUID.randomUUID());
        request.setStatus(DocStatus.APPROVED);

        mockMvc.perform(patch("/document/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/document/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void search_ordersByModifiedAtDescThenDocType() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        insertDocument("rt-web1", "rt", now.minusMinutes(10));
        insertDocument("rt-web2", "rt", now.minusMinutes(5));
        insertDocument("tp-web3", "tp", now.minusMinutes(5));

        mockMvc.perform(get("/document/search").param("drn", "web"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].drn").value("rt-web2"))
                .andExpect(jsonPath("$.content[1].drn").value("tp-web3"))
                .andExpect(jsonPath("$.content[2].drn").value("rt-web1"));
    }

    @Test
    void search_paginatesResults() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        insertDocument("rt-pg1", "rt", now.minusMinutes(10));
        insertDocument("rt-pg2", "rt", now.minusMinutes(5));
        insertDocument("tp-pg3", "tp", now.minusMinutes(1));

        mockMvc.perform(get("/document/search").param("drn", "pg").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].drn").value("tp-pg3"))
                .andExpect(jsonPath("$.content[1].drn").value("rt-pg2"));
    }

    @Test
    void searchByPost_appliesBodyCriteriaAndPaging() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        insertDocument("rt-post1", "rt", now.minusMinutes(10));
        insertDocument("rt-post2", "rt", now.minusMinutes(5));
        insertDocument("tp-post3", "tp", now.minusMinutes(1));

        mockMvc.perform(post("/document/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drn\":\"post\",\"page\":0,\"size\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].drn").value("tp-post3"))
                .andExpect(jsonPath("$.content[1].drn").value("rt-post2"));
    }

    @Test
    void searchByPost_filtersByDocCode() throws Exception {
        SaveDocumentRequest request = saveRequest(DocType.rt, DocStatus.SUBMITTED, "coded");
        request.setDocCode("DC-POST");
        mockMvc.perform(post("/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveRequest(DocType.rt, DocStatus.SUBMITTED, "other"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/document/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drn\":\"rt-\",\"docCode\":\"DC-POST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].docCode").value("DC-POST"))
                .andExpect(jsonPath("$.content[0].content").value("coded"));
    }

    @Test
    void search_missingDrn_returns400ForGetAndPost() throws Exception {
        mockMvc.perform(get("/document/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("invalid.drn"));
        mockMvc.perform(post("/document/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("invalid.drn"));
    }

    private void insertDocument(String drn, String docType, LocalDateTime modifiedAt) {
        Document document = new Document();
        document.setDrn(drn);
        document.setDocType(DocType.valueOf(docType));
        document.setStatus(DocStatus.SUBMITTED);
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
}
