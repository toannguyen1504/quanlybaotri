package com.example.quanlybaotri.telegram.persistence;

import com.example.quanlybaotri.telegram.domain.TelegramUpdateState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramUpdateStateRepository
    extends JpaRepository<TelegramUpdateState, Short> {}
