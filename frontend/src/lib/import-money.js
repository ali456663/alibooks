const LIMIT = 2147483647n;

export function parseSekMinor(value) {
  if (typeof value !== "string") throw new Error("Belopp maste vara text fran originalfilen.");
  const text = value.trim().replace(/\s*(?:SEK|kr)$/i, "").trim();
  if (!/^[+-]?(?:\d+|\d{1,3}(?:[ \u00a0\u202f]\d{3})+)(?:[.,]\d{1,2})?$/.test(text)) {
    throw new Error("Ogiltigt eller tvetydigt belopp i importfilen.");
  }
  const normalized = text.replace(/[ \u00a0\u202f]/g, "").replace(",", ".");
  const negative = normalized.startsWith("-");
  const [whole, fraction = ""] = normalized.replace(/^[+-]/, "").split(".");
  const minor = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
  if (minor > LIMIT * 100n) throw new Error("Beloppet overskrider AliBooks nuvarande beloppsgrans.");
  return negative ? -minor : minor;
}

export function wholeSekFromMinor(minor) {
  if (minor % 100n !== 0n) throw new Error("Importen innehaller oren. Bokforing stoppad tills AliBooks har fullt ore-stod; belopp avrundas inte.");
  if (minor > LIMIT * 100n || minor < -LIMIT * 100n) throw new Error("Beloppet overskrider AliBooks nuvarande beloppsgrans.");
  return Number(minor / 100n);
}

export function bankRowAmount(cells, { amountIndex, creditIndex, debitIndex }) {
  if (amountIndex >= 0) return wholeSekFromMinor(parseSekMinor(cells[amountIndex]));
  if (creditIndex < 0 && debitIndex < 0) throw new Error("Bankfilen saknar en entydig beloppskolumn.");
  const read = index => index < 0 || !String(cells[index] ?? "").trim() ? 0n : parseSekMinor(cells[index]);
  const credit = read(creditIndex);
  const debit = read(debitIndex);
  if (credit < 0n || debit < 0n || (credit !== 0n && debit !== 0n)) {
    throw new Error("Bankraden har tvetydiga in- och utbetalningsbelopp.");
  }
  return wholeSekFromMinor(credit - debit);
}

export function verifiedSieLines(voucher) {
  if (!voucher || voucher.moneyVersion !== 1 || voucher.unsupportedTransactions || !voucher.date
      || voucher.transactionsList?.length !== 2) throw new Error("SIE-verifikatet maste analyseras pa nytt eller granskas manuellt.");
  const date = new Date(`${voucher.date}T00:00:00Z`);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(voucher.date) || Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== voucher.date) {
    throw new Error("SIE-verifikatet saknar giltigt originaldatum.");
  }
  const amounts = voucher.transactionsList.map(row => {
    const minor = parseSekMinor(row.sourceAmount);
    if (minor.toString() !== row.amountMinor) throw new Error("SIE-beloppet stammer inte med originalanalysen.");
    const amount = wholeSekFromMinor(minor);
    if (row.debit !== Math.max(amount, 0) || row.credit !== Math.max(-amount, 0)) {
      throw new Error("SIE-raderna stammer inte med originalbeloppen.");
    }
    return amount;
  });
  if (amounts[0] === 0 || amounts[0] !== -amounts[1]) throw new Error("SIE-verifikatet balanserar inte exakt.");
  return { debitLine: voucher.transactionsList[amounts[0] > 0 ? 0 : 1],
    creditLine: voucher.transactionsList[amounts[0] < 0 ? 0 : 1], amount: Math.abs(amounts[0]) };
}
