package se.cloudshop.expense;

public record ReceiptHashRepairResult(
    int scannedCount,
    int repairedCount,
    int alreadyHasHashCount,
    int noReceiptCount,
    int missingFileCount,
    int lockedSkippedCount,
    int failedCount
) {
}
