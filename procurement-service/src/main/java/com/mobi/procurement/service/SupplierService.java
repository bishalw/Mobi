package com.mobi.procurement.service;

import com.mobi.procurement.dto.SupplierCatalogueRequest;
import com.mobi.procurement.dto.SupplierProductRequest;
import com.mobi.procurement.dto.SupplierProductResponse;
import com.mobi.procurement.dto.SupplierRequest;
import com.mobi.procurement.dto.SupplierResponse;
import com.mobi.procurement.entity.Supplier;
import com.mobi.procurement.entity.SupplierProduct;
import com.mobi.procurement.exception.BadRequestException;
import com.mobi.procurement.exception.ConflictException;
import com.mobi.procurement.exception.NotFoundException;
import com.mobi.procurement.repository.SupplierProductRepository;
import com.mobi.procurement.repository.SupplierRepository;
import com.mobi.procurement.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;

    public SupplierService(SupplierRepository supplierRepository, SupplierProductRepository supplierProductRepository) {
        this.supplierRepository = supplierRepository;
        this.supplierProductRepository = supplierProductRepository;
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> list() {
        return supplierRepository.findAllByOrderByNameAsc().stream().map(SupplierResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        return SupplierResponse.from(find(id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        String name = request.name().trim();
        if (supplierRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A supplier named " + name + " already exists");
        }
        Supplier supplier = new Supplier(name, request.contactEmail().trim());
        if (Boolean.FALSE.equals(request.active())) {
            supplier.update(supplier.getName(), supplier.getContactEmail(), false);
        }
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier supplier = find(id);
        String name = request.name().trim();
        if (supplierRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A supplier named " + name + " already exists");
        }
        supplier.update(name, request.contactEmail().trim(),
                request.active() == null ? supplier.isActive() : request.active());
        return SupplierResponse.from(supplier);
    }

    @Transactional(readOnly = true)
    public List<SupplierProductResponse> catalogue(CurrentUser caller, Long supplierId) {
        find(supplierId);
        return supplierProductRepository.findBySupplierIdOrderByProductIdAsc(supplierId).stream()
                .map(product -> SupplierProductResponse.from(product, caller.isAdmin()))
                .toList();
    }

    /** Replaces the catalogue. Existing POs keep the unit cost they were raised at. */
    @Transactional
    public List<SupplierProductResponse> replaceCatalogue(Long supplierId, SupplierCatalogueRequest request) {
        find(supplierId);
        Set<Long> seen = new HashSet<>();
        for (SupplierProductRequest product : request.products()) {
            if (!seen.add(product.productId())) {
                throw new BadRequestException("Product " + product.productId() + " is listed twice");
            }
        }
        supplierProductRepository.deleteBySupplierId(supplierId);
        List<SupplierProduct> saved = supplierProductRepository.saveAll(request.products().stream()
                .map(p -> new SupplierProduct(supplierId, p.productId(), blankToNull(p.supplierSku()), p.unitCost()))
                .toList());
        return saved.stream().map(product -> SupplierProductResponse.from(product, true)).toList();
    }

    Supplier find(Long id) {
        return supplierRepository.findById(id).orElseThrow(() -> new NotFoundException("Supplier " + id + " not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
