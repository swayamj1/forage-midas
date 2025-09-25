package com.jpmc.midascore;

import com.jpmc.midascore.component.TransactionService;
import com.jpmc.midascore.foundation.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class TransactionListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionListener.class);
    private final TransactionService transactionService;

    public TransactionListener(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // Topic comes from your configuration: general.kafka-topic
    @KafkaListener(topics = "${general.kafka-topic}")
    public void onTransaction(@Payload Transaction transaction) {
        transactionService.processTransaction(transaction);
        log.debug("Received: {}", transaction);
    }
}