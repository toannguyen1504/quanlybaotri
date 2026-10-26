package com.example.quanlybaotri.telegram.persistence;

import com.example.quanlybaotri.telegram.domain.TelegramCommandReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramCommandReceiptRepository extends JpaRepository<TelegramCommandReceipt, String> {}
