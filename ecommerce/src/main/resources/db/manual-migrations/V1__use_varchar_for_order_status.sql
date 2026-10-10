-- MySQL ENUM columns reject newly introduced OrderStatus values such as SHIPPED.
ALTER TABLE customer_orders
    MODIFY COLUMN status VARCHAR(20) NOT NULL;
