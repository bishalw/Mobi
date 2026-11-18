package com.mobi.notification.contact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserContactRepository extends JpaRepository<UserContact, Long> {

    List<UserContact> findByRoleAndEnabledTrueOrderByUserIdAsc(String role);
}
