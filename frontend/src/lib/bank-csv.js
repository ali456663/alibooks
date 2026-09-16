import { bankRowAmount } from "./import-money.js";

export function splitCsvLine(line, separator) {
  const cells = [];
  let current = "";
  let quoted = false;
  let closed = false;

  for (let index = 0; index < line.length; index++) {
    const character = line[index];
    if (quoted) {
      if (character === '"' && line[index + 1] === '"') { current += '"'; index++; }
      else if (character === '"') { quoted = false; closed = true; }
      else current += character;
    } else if (character === separator) {
      cells.push(current.trim());
      current = "";
      closed = false;
    } else if (closed) {
      if (!/\s/.test(character)) throw new Error("Ogiltiga tecken efter citerat CSV-falt.");
    } else if (character === '"') {
      if (current.trim()) throw new Error("Ogiltiga citattecken i CSV-falt.");
      current = "";
      quoted = true;
    } else {
      current += character;
    }
  }

  if (quoted) throw new Error("Bankfilen innehaller oavslutade citattecken.");
  cells.push(current.trim());
  return cells;
}

function normalizeHeader(value) {
  return String(value || "")
    .toLowerCase()
    .replaceAll("\u00e5", "a")
    .replaceAll("\u00e4", "a")
    .replaceAll("\u00f6", "o")
    .replaceAll("å", "a")
    .replaceAll("ä", "a")
    .replaceAll("ö", "o")
    .replace(/[^a-z0-9]/g, "");
}

export function parseBankCsv(text) {
  const lines = String(text || "").split(/\r?\n/).map((line) => line.trim()).filter(Boolean);

  if (lines.length < 2) {
    return [];
  }

  const separator = lines[0].includes(";") ? ";" : ",";
  const headers = splitCsvLine(lines[0], separator).map(normalizeHeader);
  const findHeader = (names) => {
    const matches = headers.map((header, index) => names.some((name) => (name.length <= 2 ? header === name : header.includes(name))) ? index : -1).filter(index => index >= 0);
    return matches.length === 1 ? matches[0] : -1;
  };
  const dateIndex = findHeader(["datum", "date", "bokforingsdag", "transaktionsdag"]);
  const descriptionIndex = findHeader(["text", "beskrivning", "description", "meddelande", "namn"]);
  const referenceIndex = findHeader(["referens", "reference", "ocr", "meddelande"]);
  const amountIndex = findHeader(["belopp", "amount", "summa"]);
  const creditIndex = findHeader(["in", "credit", "kredit", "insattning", "insatt"]);
  const debitIndex = findHeader(["ut", "debit", "debet", "uttag"]);
  if (dateIndex < 0 || (amountIndex < 0 && creditIndex < 0 && debitIndex < 0)) {
    throw new Error("Bankfilen saknar datum eller beloppskolumn. Ingen kolumn gissas.");
  }

  return lines.slice(1).map((line, index) => {
    const cells = splitCsvLine(line, separator);
    if (cells.length !== headers.length) throw new Error(`Rad ${index + 2}: Antalet kolumner stammer inte.`);
    const date = cells[dateIndex];
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !Number.isFinite(Date.parse(date))
        || new Date(date).toISOString().slice(0, 10) !== date) {
      throw new Error(`Rad ${index + 2}: Giltigt datum i format YYYY-MM-DD kravs.`);
    }
    const fallbackDescription = cells.filter(Boolean).join(" ");
    let amount;
    try {
      amount = bankRowAmount(cells, { amountIndex, creditIndex, debitIndex });
    } catch (error) {
      throw new Error(`Rad ${index + 2}: ${error.message}`);
    }

    return {
      id: `unverified-bank-${index}`,
      date: cells[dateIndex] || "",
      description: cells[descriptionIndex] || fallbackDescription,
      reference: cells[referenceIndex] || "",
      amount,
      raw: line
    };
  }).filter((row) => row.description || row.amount);
}
