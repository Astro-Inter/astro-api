package com.astro.api.conformity.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.conformity.dto.response.ConformityImportResponse;
import com.astro.api.conformity.dto.response.ConformityImportResponse.RejectedRow;
import com.astro.api.conformity.mapper.ConformityMapper;
import com.astro.api.conformity.model.ConformityImportRow;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ConformityImportService {
    private final ConformitySpreadsheetReader conformitySpreadsheetReader;
    private final ConformityService conformityService;
    private final ConformityMapper conformityMapper;

    public ConformityImportService(ConformitySpreadsheetReader conformitySpreadsheetReader,
                                   ConformityService conformityService, ConformityMapper conformityMapper) {
        this.conformitySpreadsheetReader = conformitySpreadsheetReader;
        this.conformityService = conformityService;
        this.conformityMapper = conformityMapper;
    }
    public ConformityImportResponse importFile(MultipartFile file, AuthenticatedUser principal) {
        conformityService.authorizeImport(principal);
        List<ConformityImportRow> rows = conformitySpreadsheetReader.read(file);
        Set<String> completed = new HashSet<>();
        List<RejectedRow> rejected = new ArrayList<>();
        int inserted = 0, updated = 0;
        for (ConformityImportRow row : rows) {
            String error = row.error();
            String key = row.cpf() + ":" + row.nrId();
            if (error == null && completed.contains(key)) error = "CPF/NR duplicados; a primeira linha válida já foi processada";
            if (error == null) {
                try {
                    if (conformityService.importRow(row.cpf(), row.nrId(), row.expiresAt(), principal)) inserted++;
                    else updated++;
                    completed.add(key);
                } catch (BusinessException | ConflictException | ResourceNotFoundException failure) {
                    error = failure.getMessage();
                } catch (DataAccessException | TransactionException failure) {
                    error = "Não foi possível gravar esta linha; nenhuma alteração da linha foi confirmada";
                }
            }
            if (error != null) rejected.add(conformityMapper.toRejectedRow(row, error));
        }
        return new ConformityImportResponse(rows.size(), inserted, updated, rejected.size(), List.copyOf(rejected));
    }
}
