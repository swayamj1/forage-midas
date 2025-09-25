package com.jpmc.midascore.entity;

import java.math.BigDecimal;

public class Incentive {
    private BigDecimal amount;

    public Incentive() { }

    public Incentive(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "Incentive{amount=" + amount + "}";
    }
}