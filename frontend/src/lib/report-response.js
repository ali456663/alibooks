const contracts = {
  profit: { amounts: ["totalRevenue", "totalExpenses", "result"], rows: ["revenue", "expenses"] },
  balance: { amounts: ["totalAssets", "totalLiabilitiesAndEquity", "difference"], rows: ["assets", "liabilitiesAndEquity"] },
  vat: { amounts: ["outputVat", "inputVat", "vatToPay"], rows: [] },
  trial: {
    amounts: ["openingDebitTotal", "openingCreditTotal", "periodDebitTotal", "periodCreditTotal", "closingDebitTotal", "closingCreditTotal", "difference"],
    rows: ["lines"],
    rowAmounts: ["openingDebit", "openingCredit", "periodDebit", "periodCredit", "closingDebit", "closingCredit"]
  },
  trialMinor: {
    amounts: ["openingDebitTotalMinor", "openingCreditTotalMinor", "periodDebitTotalMinor", "periodCreditTotalMinor", "closingDebitTotalMinor", "closingCreditTotalMinor", "differenceMinor"],
    rows: ["lines"],
    rowAmounts: ["openingDebitMinor", "openingCreditMinor", "periodDebitMinor", "periodCreditMinor", "closingDebitMinor", "closingCreditMinor"]
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
  if (!valid || (kind === "trialMinor" && !validMinorTrial(data))) throw new Error(fallback);
  return data;
}

function validMinorTrial(data) {
  if (data.currencyCode !== "SEK" || data.minorUnitExponent !== 2) return false;
  const fields = contracts.trialMinor.rowAmounts;
  const accounts = new Set();
  for (const line of data.lines) {
    if (!line.accountNumber.trim() || accounts.has(line.accountNumber)
        || fields.some(field => line[field] < 0)
        || (line.openingDebitMinor > 0 && line.openingCreditMinor > 0)
        || (line.closingDebitMinor > 0 && line.closingCreditMinor > 0)) return false;
    accounts.add(line.accountNumber);
    const opening = BigInt(line.openingDebitMinor) - BigInt(line.openingCreditMinor);
    const movement = BigInt(line.periodDebitMinor) - BigInt(line.periodCreditMinor);
    const closing = BigInt(line.closingDebitMinor) - BigInt(line.closingCreditMinor);
    if (opening + movement !== closing) return false;
  }
  for (const field of fields) {
    const total = data.lines.reduce((sum, line) => sum + BigInt(line[field]), 0n);
    if (total !== BigInt(data[field.replace("Minor", "TotalMinor")])) return false;
  }
  return BigInt(data.closingDebitTotalMinor) - BigInt(data.closingCreditTotalMinor) === BigInt(data.differenceMinor);
}
