-- OrderOutboxPublisher polls every 5 seconds with
-- WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 50.
-- Unindexed, that is a full scan plus a sort on every tick.
CREATE INDEX ix_outbox_events_status_created_at
    ON outbox_events (status, created_at);

-- OrderRepository.findByUserId backs "my orders", the most-used read path.
CREATE INDEX ix_orders_user_id ON orders (user_id);

-- order_items.order_id needs no index: InnoDB creates one automatically for the
-- foreign key declared in V1.
