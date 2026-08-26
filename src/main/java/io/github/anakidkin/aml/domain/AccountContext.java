package io.github.anakidkin.aml.domain;

/**
 * Aggregated historical and behavioral metrics required by AML rules to evaluate risk.
 *
 * @param volume24h               total sum of outgoing transactions in the last 24 hours
 * @param txCount24h              total count of outgoing transactions in the last 24 hours
 * @param uniqueCounterparties24h count of distinct target accounts interacted with in the last 24 hours
 * @param p2pRatio30d             ratio of peer-to-peer transfers relative to total volume over 30 days
 * @param isDormantAccount        indicates whether the account was inactive prior to the current activity
 */
public record AccountContext(
    double volume24h,
    long txCount24h,
    int uniqueCounterparties24h,
    double p2pRatio30d,
    boolean isDormantAccount
) {
}