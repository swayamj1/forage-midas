package com.jpmc.midascore.component;

import com.jpmc.midascore.entity.Incentive;
import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import com.jpmc.midascore.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;



@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final UserRepository userRepository;
    private final TransactionRecordRepository transactionRecordRepository;
    private final RestTemplate restTemplate;
    private final String incentiveApiUrl;

    public TransactionService(UserRepository userRepository,
                              TransactionRecordRepository transactionRecordRepository,
                              RestTemplate restTemplate,
                              @Value("${incentive.api-url:http://localhost:8080/incentive}") String incentiveApiUrl) {
        this.userRepository = userRepository;
        this.transactionRecordRepository = transactionRecordRepository;
        this.restTemplate = restTemplate;
        this.incentiveApiUrl = incentiveApiUrl;
    }

    @Transactional
    public void processTransaction(Transaction tx) {
        if (tx == null) {
            log.warn("Ignoring null transaction");
            return;
        }

        long senderId = tx.getSenderId();
        long recipientId = tx.getRecipientId();

        Optional<UserRecord> senderOpt = userRepository.findById(senderId);
        Optional<UserRecord> recipientOpt = userRepository.findById(recipientId);

        if (senderOpt.isEmpty() || recipientOpt.isEmpty()) {
            log.debug("Discarding transaction: invalid user(s). senderId={}, recipientId={}", senderId, recipientId);
            return;
        }

        UserRecord sender = senderOpt.get();
        UserRecord recipient = recipientOpt.get();

        BigDecimal amount = BigDecimal.valueOf(tx.getAmount()).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            log.debug("Discarding transaction: non-positive amount {}", amount);
            return;
        }

        float senderBal = sender.getBalance();
        float recipientBal = recipient.getBalance();
        float amt = amount.floatValue();

        if (senderBal < amt) {
            log.debug("Discarding transaction: insufficient funds. senderId={}, balance={}, amount={}",
                    senderId, senderBal, amt);
            return;
        }

        // Fetch incentive from external API (fail-safe to zero)
        BigDecimal incentive = fetchIncentive(tx);
        float inc = incentive.floatValue();

        // Apply balances: sender pays amount only; recipient gets amount + incentive
        sender.setBalance(senderBal - amt);
        recipient.setBalance(recipientBal + amt + inc);

        userRepository.save(sender);
        userRepository.save(recipient);

        TransactionRecord record = new TransactionRecord();
        record.setSender(sender);
        record.setRecipient(recipient);
        record.setAmount(amount);
        record.setIncentive(incentive);
        transactionRecordRepository.save(record);

        log.debug("Recorded transaction: senderId={}, recipientId={}, amount={}, incentive={}",
                senderId, recipientId, amount, incentive);
    }

    private BigDecimal fetchIncentive(Transaction tx) {
        try {
            Incentive resp = restTemplate.postForObject(incentiveApiUrl, tx, Incentive.class);
            BigDecimal value = (resp == null || resp.getAmount() == null) ? BigDecimal.ZERO : resp.getAmount();
            if (value.signum() < 0) {
                // Defensive: never allow negative incentive
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            return value.setScale(2, RoundingMode.HALF_UP);
        } catch (RestClientException ex) {
            log.warn("Incentive API call failed; defaulting incentive to 0. Cause: {}", ex.getMessage());
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }
}