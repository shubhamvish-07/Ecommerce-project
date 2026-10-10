package com.ecommerce.ecommerce.repository;

import com.ecommerce.ecommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {


    List<Product> findByActiveTrue();

    List<Product> findByCategoryAndActiveTrue(String category);

    List<Product> findAllByIdIn(Collection<Long> ids);

    @Modifying
    @Query("""
            update Product p
            set p.stock = p.stock - :quantity
            where p.id = :id and p.active = true and p.stock >= :quantity
            """)
    int decrementStockIfAvailable(@Param("id") Long id, @Param("quantity") int quantity);

    @Query(value = "select stock from products where id = :id for update", nativeQuery = true)
    Integer findStockForUpdate(@Param("id") Long id);

    @Modifying
    @Query("""
            update Product p
            set p.stock = p.stock + :quantity
            where p.id = :id and p.stock <= :maximumStock
            """)
    int restoreStockIfWithinRange(
            @Param("id") Long id,
            @Param("quantity") int quantity,
            @Param("maximumStock") int maximumStock);

}
