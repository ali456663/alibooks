package se.cloudshop.bank;

import java.time.LocalDate;

public record BankImportRow(String bankRowId, LocalDate date, String description, String reference, int amount) {
}
