const states = ["MATCHED", "NO_DATA", "REVIEW_REQUIRED", "UNSUPPORTED_METHOD"];
const integer = value => Number.isSafeInteger(value) && Math.abs(value) <= 2147483647;
const count = value => integer(value) && value >= 0;

function accountIsValid(row) {
  if (!row || !["1510", "2440"].includes(row.accountNumber) || !states.includes(row.status)
      || !integer(row.subledgerBalance) || !integer(row.ledgerBalance)
      || !count(row.unlinkedEntryCount) || !count(row.undatedEntryCount)
      || !Array.isArray(row.differences)) return false;
  const ids = new Set();
  if (!row.differences.every(item => {
    if (!item || !Number.isSafeInteger(item.invoiceId) || item.invoiceId <= 0 || ids.has(item.invoiceId)
        || !integer(item.subledgerBalance) || !integer(item.ledgerBalance) || !integer(item.difference)
        || item.difference === 0 || item.difference !== item.ledgerBalance - item.subledgerBalance) return false;
    ids.add(item.invoiceId);
    return true;
  })) return false;
  if (row.status === "UNSUPPORTED_METHOD") return row.difference === null && row.differences.length === 0;
  if (!integer(row.difference) || row.difference !== row.ledgerBalance - row.subledgerBalance) return false;
  const needsReview = row.difference !== 0 || row.differences.length > 0 || row.unlinkedEntryCount > 0 || row.undatedEntryCount > 0;
  if ((row.status === "REVIEW_REQUIRED") !== needsReview) return false;
  return row.status !== "NO_DATA" || (row.subledgerBalance === 0 && row.ledgerBalance === 0);
}

export async function readSubledgerResponse(response, expectedDate, fallback) {
  let data;
  try { data = await response.json(); } catch { throw new Error(fallback); }
  if (!response.ok) throw new Error(typeof data?.message === "string" && data.message.trim() ? data.message : fallback);
  if (!data || data.asOf !== expectedDate || typeof data.accountingMethod !== "string" || !data.accountingMethod
      || !states.includes(data.status) || !Array.isArray(data.accounts) || data.accounts.length !== 2
      || new Set(data.accounts.map(row => row?.accountNumber)).size !== 2 || !data.accounts.every(accountIsValid)) {
    throw new Error(fallback);
  }
  const expectedStatus = data.accountingMethod !== "INVOICE_METHOD" ? "UNSUPPORTED_METHOD"
    : data.accounts.some(row => row.status === "REVIEW_REQUIRED") ? "REVIEW_REQUIRED"
    : data.accounts.every(row => row.status === "NO_DATA") ? "NO_DATA" : "MATCHED";
  if (data.status !== expectedStatus
      || data.accounts.some(row => (row.status === "UNSUPPORTED_METHOD") !== (data.accountingMethod !== "INVOICE_METHOD"))) {
    throw new Error(fallback);
  }
  return data;
}
