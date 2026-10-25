package com.astro.api.nrreview.controller;

import com.astro.api.common.response.ApiResult;
import com.astro.api.nrreview.dto.NrApprovalRequest;
import com.astro.api.nrreview.dto.NrApprovalResult;
import com.astro.api.nrreview.dto.NrSnapshot;
import com.astro.api.nrreview.dto.NrUpdateRequest;
import com.astro.api.nrreview.service.NrReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/nrs")
@PreAuthorize("hasRole('ADMIN')")
public class NrReviewController {
    private final NrReviewService nrReviewService;

    public NrReviewController(NrReviewService nrReviewService) {
        this.nrReviewService = nrReviewService;
    }

    @GetMapping("/pendentes")
    public ApiResult<List<NrSnapshot>> pending(HttpServletRequest request) {
        List<NrSnapshot> snapshots = nrReviewService.list(true);
        return success(snapshots, request);
    }

    @GetMapping("/pendentes/{id}")
    public ApiResult<NrSnapshot> candidate(@PathVariable int id, HttpServletRequest request) {
        NrSnapshot snapshot = nrReviewService.get(id, true);
        return success(snapshot, request);
    }

    @GetMapping("/catalogo")
    public ApiResult<List<NrSnapshot>> catalog(HttpServletRequest request) {
        List<NrSnapshot> snapshots = nrReviewService.list(false);
        return success(snapshots, request);
    }

    @GetMapping("/catalogo/{id}")
    public ApiResult<NrSnapshot> approved(@PathVariable int id, HttpServletRequest request) {
        NrSnapshot snapshot = nrReviewService.get(id, false);
        return success(snapshot, request);
    }

    @PutMapping("/catalogo/{id}")
    public ApiResult<NrSnapshot> update(@PathVariable int id, @Valid @RequestBody NrUpdateRequest requestBody,
            HttpServletRequest request) {
        NrSnapshot snapshot = nrReviewService.update(id, requestBody);
        return success(snapshot, request);
    }

    @PostMapping("/pendentes/{id}/aprovar")
    public ApiResult<NrApprovalResult> approve(@PathVariable int id, @Valid @RequestBody NrApprovalRequest requestBody,
            HttpServletRequest request) {
        NrApprovalResult result = nrReviewService.approve(id, requestBody);
        return success(result, request);
    }

    private static <T> ApiResult<T> success(T data, HttpServletRequest request) {
        return ApiResult.success("Operação de NR concluída com sucesso.", data, request.getRequestURI());
    }
}
