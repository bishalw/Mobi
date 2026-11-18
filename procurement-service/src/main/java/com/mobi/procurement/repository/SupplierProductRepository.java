package com.mobi.procurement.repository;

import com.mobi.procurement.entity.SupplierProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SupplierProductRepository extends JpaRepository<SupplierProduct, SupplierProduct.Key> {

    List<SupplierProduct> findBySupplierIdOrderByProductIdAsc(Long supplierId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SupplierProduct sp where sp.supplierId = :supplierId")
    void deleteBySupplierId(Long supplierId);
}
