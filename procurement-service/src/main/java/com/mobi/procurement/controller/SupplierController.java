package com.mobi.procurement.controller;

import com.mobi.procurement.dto.SupplierCatalogueRequest;
import com.mobi.procurement.dto.SupplierProductResponse;
import com.mobi.procurement.dto.SupplierRequest;
import com.mobi.procurement.dto.SupplierResponse;
import com.mobi.procurement.security.CurrentUser;
import com.mobi.procurement.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Reads: ADMIN and WAREHOUSE. Writes: ADMIN. See SecurityConfig. */
@RestController
@RequestMapping("/procurementapi/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public List<SupplierResponse> list() {
        return supplierService.list();
    }

    @GetMapping("/{id}")
    public SupplierResponse get(@PathVariable Long id) {
        return supplierService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return supplierService.create(request);
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }

    /** Unit costs are included for admins only. */
    @GetMapping("/{id}/products")
    public List<SupplierProductResponse> catalogue(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return supplierService.catalogue(CurrentUser.from(jwt), id);
    }

    @PutMapping("/{id}/products")
    public List<SupplierProductResponse> replaceCatalogue(@PathVariable Long id,
                                                          @Valid @RequestBody SupplierCatalogueRequest request) {
        return supplierService.replaceCatalogue(id, request);
    }
}
