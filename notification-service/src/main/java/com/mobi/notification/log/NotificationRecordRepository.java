package com.mobi.notification.log;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {

    boolean existsByEventIdAndChannel(String eventId, Channel channel);
}
