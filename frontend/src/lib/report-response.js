const contracts = {
  profit: { amounts: ["totalRevenue", "totalExpenses", "result"], rows: ["revenue", "expenses"] },
  balance: { amounts: ["totalAssets", "totalLiabilitiesAndEquity", "difference"], rows: ["assets", "liabilitiesAndEquity"] },
  vat: { amounts: ["outputVat", "inputVat", "vatToPay"], rows: [] },
  trial: {
    amounts: ["openingDebitTotal", "openingCreditTotal", "periodDebitTotal", "periodCreditTotal", "closingDebitTotal", "closingCreditTotal", "difference"],
    rows: ["lines"],
    rowAmounts: ["openingDebit", "openingCredit", "periodDebit", "periodCredit", "closingDebit", "closingCredit"]
  }
};

export async function readReportResponse(response, kind, fallback) {
  const data = await response.json();
  if (!response.ok) {
    const message = [data?.message, data?.detail, data?.error].find(value => typeof value === "string" && value.trim());
    throw new Error(message || fallback);
  }
  const contract = contracts[kind];
  if (!contract) throw new Error(fallback);
  const rowAmounts = contract.rowAmounts || ["amount"];
  const valid = data && typeof data === "object" && !Array.isArray(data)
    && contract.amounts.every(field => Number.isSafeInteger(data[field]))
    && contract.rows.every(field => Array.isArray(data[field]) && data[field].every(row =>
      row && typeof row.accountNumber === "string" && typeof row.accountName === "string"
      && rowAmounts.every(amount => Number.isSafeInteger(row[amount]))));
  if (!valid) throw new Error(fallback);
  return data;
}
