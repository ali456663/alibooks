export function bankRowPayload(row) {
  if (!row || !/^bank-v1-[a-f0-9]{64}-\d+$/.test(row.id || "")
      || !/^\d{4}-\d{2}-\d{2}$/.test(row.date || "")
      || !Number.isSafeInteger(row.amount) || row.amount === 0 || Math.abs(row.amount) > 2147483647) {
    throw new Error("Ogiltig eller gammal bankrad. Las in CSV-filen igen.");
  }
  return { bankRowId: row.id, date: row.date, amount: row.amount, description: row.description || "", reference: row.reference || "" };
}

export function bankPaymentPayload(row, remainingAmount) {
  const bankRow = bankRowPayload(row);
  if (bankRow.amount <= 0 || !Number.isSafeInteger(remainingAmount) || bankRow.amount > remainingAmount) {
    throw new Error("Bankbeloppet maste vara positivt och rymmas i fakturans restsaldo. Overskott kraver separat avstamning.");
  }
  return { bankRow, paymentDate: bankRow.date, paidAmount: bankRow.amount,
    paymentReference: bankRow.reference || bankRow.description || "Bank CSV" };
}

export function uniqueBankInvoice(row, invoices, remaining, number) {
  if (!row || row.amount <= 0) return null;
  const text = `${row.description || ""} ${row.reference || ""}`.toLowerCase();
  const containsReference = value => {
    const reference = String(value ?? "").trim();
    if (!reference) return false;
    const escaped = reference.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    return new RegExp(`(^|[^\\p{L}\\p{N}])${escaped}(?=$|[^\\p{L}\\p{N}])`, "iu").test(text);
  };
  const eligible = invoices.filter(invoice => ["SENT", "PARTIALLY_PAID"].includes(invoice.status) && !invoice.creditInvoice && remaining(invoice) > 0);
  const references = eligible.filter(invoice => [invoice.ocrNumber, number(invoice)]
    .some(containsReference));
  if (references.length) return references.length === 1 ? references[0] : null;
  const candidates = eligible.filter(invoice => row.amount === remaining(invoice)
    && invoice.customerName && text.includes(invoice.customerName.toLowerCase()));
  return candidates.length === 1 ? candidates[0] : null;
}

export async function bankRequest(url, headers, payload, method = "POST", fetcher = fetch) {
  const response = await fetcher(url, { method, headers: { ...headers, "Content-Type": "application/json" },
    body: method === "DELETE" ? undefined : JSON.stringify(payload) });
  if (!response.ok) {
    throw new Error(response.status === 409
      ? "Bankraden finns redan eller konflikten kraver granskning. Uppdatera historiken innan du forsoker igen."
      : `Bankanropet misslyckades (${response.status}). Raden behalls; kontrollera serverhistoriken fore aterforsok.`);
  }
  if (method === "DELETE") return null;
  const data = await response.json();
  if (!data || !Number.isSafeInteger(data.id) || data.id <= 0) throw new Error("Ogiltigt banksvar. Kontrollera serverhistoriken fore aterforsok.");
  return data;
}

export async function identifyBankRows(rows, cryptoProvider = globalThis.crypto) {
  const counts = new Map();
  const identified = [];
  for (const row of rows) {
    const canonical = JSON.stringify(["1930", row.date, row.amount, row.reference || "", row.description || ""]);
    const occurrence = counts.get(canonical) || 0;
    counts.set(canonical, occurrence + 1);
    const hash = await cryptoProvider.subtle.digest("SHA-256", new TextEncoder().encode(canonical));
    const hex = Array.from(new Uint8Array(hash), b => b.toString(16).padStart(2, "0")).join("");
    identified.push({ ...row, id: `bank-v1-${hex}-${occurrence}` });
  }
  return identified;
}
