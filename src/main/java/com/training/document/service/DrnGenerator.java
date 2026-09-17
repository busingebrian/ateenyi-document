package com.training.document.service;

import com.training.document.entity.DocType;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DrnGenerator {

    private static final String SEQUENCE_NAME = "document_drn_seq";
    private static final int NUMERIC_PART_LENGTH = 6;
    private static final long MAX_SEQUENCE_VALUE = 999_999L;

    private final JdbcTemplate jdbcTemplate;
    private final String qualifiedSequenceName;

    public DrnGenerator(JdbcTemplate jdbcTemplate,
                        @Value("${spring.jpa.properties.hibernate.default_schema:document}") String schema) {
        this.jdbcTemplate = jdbcTemplate;
        this.qualifiedSequenceName = schema + "." + SEQUENCE_NAME;
    }

    @PostConstruct
    void createSequence() {
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS " + qualifiedSequenceName + " START WITH 1 INCREMENT BY 1");
    }

    public String generate(DocType docType) {
        Long next = jdbcTemplate.queryForObject("SELECT NEXTVAL('" + qualifiedSequenceName + "')", Long.class);
        if (next == null || next > MAX_SEQUENCE_VALUE) {
            throw new IllegalStateException(
                    "DRN sequence exhausted the " + NUMERIC_PART_LENGTH + "-digit numeric range for docType " + docType);
        }
        return docType.name() + "-" + String.format("%0" + NUMERIC_PART_LENGTH + "d", next);
    }
}
