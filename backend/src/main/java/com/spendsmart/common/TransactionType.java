package com.spendsmart.common;

/** Direction of money flow. Shared by transactions and categories so a category can only hold matching entries. */
public enum TransactionType {
    INCOME,
    EXPENSE
}
