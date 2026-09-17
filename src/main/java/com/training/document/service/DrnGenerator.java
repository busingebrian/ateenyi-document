package com.training.document.service;

import com.training.document.entity.DocType;
import com.training.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class DrnGenerator {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int RANDOM_PART_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;

    private final SecureRandom random = new SecureRandom();
    private final DocumentRepository documentRepository;

    public String generate(DocType docType) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String drn = docType.name() + "-" + randomAlphanumeric();
            if (documentRepository.findByDrn(drn).isEmpty()) {
                return drn;
            }
        }
        throw new IllegalStateException("Could not generate a unique DRN for docType " + docType);
    }

    private String randomAlphanumeric() {
        StringBuilder sb = new StringBuilder(RANDOM_PART_LENGTH);
        for (int i = 0; i < RANDOM_PART_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
