-- Orders can be refunded by an admin after payment (POST /orderapi/orders/{id}/refund).
ALTER TABLE orders
    MODIFY status ENUM('CANCELLED','CONFIRMED','INVENTORY_RESERVED','PENDING','REFUNDED') NOT NULL;
