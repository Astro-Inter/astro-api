package com.astro.api.conformity.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.*;
import com.astro.api.conformity.dto.response.ConformityImportResponse;
import com.astro.api.conformity.dto.response.ConformityImportResponse.RejectedRow;
import com.astro.api.conformity.mapper.ConformityMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
public class ConformityImportService {
    private final ConformitySpreadsheetReader reader;
    private final ConformityService service;
    private final ConformityMapper mapper;
    public ConformityImportService(ConformitySpreadsheetReader reader, ConformityService service, ConformityMapper mapper) {
        this.reader = reader; this.service = service; this.mapper = mapper;
    }
    public ConformityImportResponse importFile(MultipartFile file, AuthenticatedUser principal) {
        service.authorizeImport(principal);
        var rows = reader.read(file);
        Set<String> completed = new HashSet<>();
        List<RejectedRow> rejected = new ArrayList<>();
        int inserted = 0, updated = 0;
        for (var row : rows) {
            String error = row.error();
            String key = row.cpf() + ":" + row.nrId();
            if (error == null && completed.contains(key)) error = "CPF/NR duplicados; a primeira linha válida já foi processada";
            if (error == null) {
                try {
                    if (service.importRow(row.cpf(), row.nrId(), row.expiresAt(), principal)) inserted++;
                    else updated++;
                    completed.add(key);
                } catch (BusinessException | ConflictException | ResourceNotFoundException failure) {
                    error = failure.getMessage();
                } catch (DataAccessException | TransactionException failure) {
                    error = "Não foi possível gravar esta linha; nenhuma alteração da linha foi confirmada";
                }
            }
            if (error != null) rejected.add(mapper.toRejectedRow(row, error));
        }
        return new ConformityImportResponse(rows.size(), inserted, updated, rejected.size(), List.copyOf(rejected));
    }
}
