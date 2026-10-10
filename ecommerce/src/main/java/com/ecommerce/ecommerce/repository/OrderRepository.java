package com.ecommerce.ecommerce.repository;

import com.ecommerce.ecommerce.entity.CustomerOrder;
import com.ecommerce.ecommerce.enums.OrderStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<CustomerOrder> findByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<CustomerOrder> findByIdAndUser_Id(Long id, Long userId);

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    List<CustomerOrder> findAllByOrderByCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    List<CustomerOrder> findByStatusOrderByCreatedAtDescIdDesc(OrderStatus status);

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Optional<CustomerOrder> findAdminOrderById(Long id);

    @Modifying
    @Query("""
            update CustomerOrder o
            set o.status = :newStatus
            where o.id = :id and o.status = :currentStatus
            """)
    int updateStatusIfCurrent(
            @Param("id") Long id,
            @Param("currentStatus") OrderStatus currentStatus,
            @Param("newStatus") OrderStatus newStatus);
}
