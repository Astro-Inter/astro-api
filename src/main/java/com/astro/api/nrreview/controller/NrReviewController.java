package com.astro.api.nrreview.controller;

import com.astro.api.common.response.ApiResult;
import com.astro.api.nrreview.dto.*;
import com.astro.api.nrreview.service.NrReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/nrs")
@PreAuthorize("hasRole('ADMIN')")
public class NrReviewController {
    private final NrReviewService service;
    public NrReviewController(NrReviewService service) { this.service = service; }

    @GetMapping("/pendentes")
    public ApiResult<List<NrSnapshot>> pending(HttpServletRequest request) { return success(service.list(true), request); }
    @GetMapping("/pendentes/{id}")
    public ApiResult<NrSnapshot> candidate(@PathVariable int id, HttpServletRequest request) { return success(service.get(id, true), request); }
    @GetMapping("/catalogo")
    public ApiResult<List<NrSnapshot>> catalog(HttpServletRequest request) { return success(service.list(false), request); }
    @GetMapping("/catalogo/{id}")
    public ApiResult<NrSnapshot> approved(@PathVariable int id, HttpServletRequest request) { return success(service.get(id, false), request); }
    @PutMapping("/catalogo/{id}")
    public ApiResult<NrSnapshot> update(@PathVariable int id, @Valid @RequestBody NrUpdateRequest body, HttpServletRequest request) { return success(service.update(id, body), request); }
    @PostMapping("/pendentes/{id}/aprovar")
    public ApiResult<NrApprovalResult> approve(@PathVariable int id, @Valid @RequestBody NrApprovalRequest body, HttpServletRequest request) { return success(service.approve(id, body), request); }
    private static <T> ApiResult<T> success(T data, HttpServletRequest request) { return ApiResult.success("Operação de NR concluída com sucesso.", data, request.getRequestURI()); }
}
