import { parseSekMinor } from "./import-money.js";

function normalizeSieDate(value = "") {
  if (!/^\d{8}$/.test(value)) return "";
  return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)}`;
}

export function analyzeSieText(text = "") {
  const lines = text.split(/\r?\n/);
  const vouchers = [];
  const accounts = new Set();
  let currentVoucher = null;

  function closeVoucher() {
    if (!currentVoucher) return;
    const differenceMinor = BigInt(currentVoucher.sumMinor || "0");
    if (differenceMinor > BigInt(Number.MAX_SAFE_INTEGER) || differenceMinor < -BigInt(Number.MAX_SAFE_INTEGER)) {
      throw new Error("SIE-differensen overskrider rapportgransen.");
    }
    const difference = Number(differenceMinor) / 100;
    vouchers.push({
      ...currentVoucher,
      moneyVersion: 1,
      difference,
      simpleManualImport: currentVoucher.transactionsList.length === 2
        && differenceMinor === 0n
        && !currentVoucher.unsupportedTransactions
        && currentVoucher.transactionsList.every((row) => BigInt(row.amountMinor) % 100n === 0n)
        && currentVoucher.transactionsList.some((line) => line.debit > 0)
        && currentVoucher.transactionsList.some((line) => line.credit > 0)
    });
    currentVoucher = null;
  }

  lines.forEach((rawLine) => {
    const line = rawLine.trim();

    if (/^#VER(?:\s|$)/.test(line)) {
      closeVoucher();
      const dateMatch = line.match(/\s(\d{8})(?:\s|$)/);
      const quotedValues = [...line.matchAll(/"([^"]*)"/g)].map((match) => match[1]);
      currentVoucher = {
        key: `${vouchers.length + 1}-${dateMatch?.[1] || "nodate"}`,
        date: normalizeSieDate(dateMatch?.[1] || ""),
        number: quotedValues[1] || String(vouchers.length + 1),
        description: quotedValues[2] || quotedValues[quotedValues.length - 1] || "SIE-verifikat",
        transactions: 0,
        transactionsList: [],
        sumMinor: "0"
      };
      return;
    }

    if (/^#(?:R|B)?TRANS(?:\s|$)/.test(line)) {
      const match = line.match(/^#(?:R|B)?TRANS\s+(\d+)\s+(?:\{[^}]*\}\s+)?([^\s]+)(?:\s|$)/);
      if (!match) throw new Error("Ogiltig transaktionsrad i SIE-filen.");

      if (!currentVoucher) {
        currentVoucher = {
          key: `${vouchers.length + 1}-nodate`,
          date: "",
          number: String(vouchers.length + 1),
          description: "SIE-verifikat",
          transactions: 0,
          transactionsList: [],
          sumMinor: "0"
        };
      }

      const amountMinor = parseSekMinor(match[2]);
      const amount = Number(amountMinor) / 100;
      const quotedValues = [...line.matchAll(/"([^"]*)"/g)].map((quoteMatch) => quoteMatch[1]);
      accounts.add(match[1]);
      currentVoucher.transactions += 1;
      currentVoucher.sumMinor = (BigInt(currentVoucher.sumMinor || "0") + amountMinor).toString();
      if (!/^#TRANS\s/.test(line)) currentVoucher.unsupportedTransactions = true;
      currentVoucher.transactionsList.push({
        account: match[1],
        debit: amount > 0 ? amount : 0,
        credit: amount < 0 ? -amount : 0,
        sourceAmount: match[2],
        amountMinor: amountMinor.toString(),
        amount,
        description: quotedValues[quotedValues.length - 1] || ""
      });
    }
  });

  closeVoucher();

  const dates = vouchers.map((voucher) => voucher.date).filter(Boolean).sort();
  const totalTransactions = vouchers.reduce((sum, voucher) => sum + voucher.transactions, 0);
  const totalDifferenceMinor = vouchers.reduce((sum, voucher) => sum + BigInt(voucher.sumMinor || "0"), 0n);
  if (totalDifferenceMinor > BigInt(Number.MAX_SAFE_INTEGER) || totalDifferenceMinor < -BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new Error("SIE-differensen overskrider rapportgransen.");
  }
  const unbalancedVouchers = vouchers.filter((voucher) => BigInt(voucher.sumMinor || "0") !== 0n).length;
  const accountList = [...accounts].sort((first, second) => Number(first) - Number(second));

  return {
    kind: "SIE",
    vouchers: vouchers.length,
    transactions: totalTransactions,
    accounts: accountList.length,
    sampleAccounts: accountList.slice(0, 8).join(", "),
    unbalancedVouchers,
    totalDifference: Number(totalDifferenceMinor) / 100,
    firstDate: dates[0] || "",
    lastDate: dates[dates.length - 1] || "",
    previewVouchers: vouchers.slice(0, 25).map((voucher, index) => ({
      ...voucher,
      key: voucher.key || `${index + 1}-${voucher.date || "nodate"}`
    }))
  };
}

