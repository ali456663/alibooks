import { useEffect, useRef, useState } from "react";
import "./bank-journal-link.css";

export default function BankJournalLink({ entry, apiUrl, token, language, onMatched }) {
  const [candidates, setCandidates] = useState(null);
  const [selected, setSelected] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const generation = useRef(0);
  const sv = language === "sv";
  useEffect(() => {
    generation.current++;
    setCandidates(null);
    setSelected("");
    setError("");
    setBusy(false);
    return () => { generation.current++; };
  }, [entry.id, token, apiUrl, entry.journalEntryId]);

  async function request(save) {
    if (!token || busy) return;
    const current = ++generation.current;
    setBusy(true);
    setError("");
    try {
      const response = await fetch(`${apiUrl}/bank-reconciliations/${entry.id}/${save ? "journal-link" : "journal-candidates"}`, {
        method: save ? "POST" : "GET",
        headers: { Authorization: `Bearer ${token}`, ...(save ? { "Content-Type": "application/json" } : {}) },
        ...(save ? { body: JSON.stringify({ journalEntryId: Number(selected) }) } : {}),
      });
      if (!response.ok) throw new Error(`${sv ? "Kopplingen kunde inte kontrolleras eller sparas" : "Could not check or save link"} (${response.status}).`);
      const data = await response.json();
      if (current !== generation.current) return;
      if (save) {
        if (data?.id !== entry.id || data?.journalEntryId !== Number(selected)) throw new Error(sv ? "Servern bekraftade inte kopplingen." : "Server did not confirm link.");
        onMatched(data);
      } else {
        if (!Array.isArray(data) || data.some(row => !Number.isSafeInteger(row.id) || row.id <= 0
            || typeof row.voucherNumber !== "string" || row.date !== (entry.bankDate || entry.date) || row.amount !== entry.amount)) {
          throw new Error(sv ? "Ogiltigt svar fran servern." : "Invalid server response.");
        }
        setCandidates(data);
        setSelected("");
      }
    } catch (failure) {
      if (current === generation.current) setError(failure.message);
    } finally {
      if (current === generation.current) setBusy(false);
    }
  }

  if (entry.status !== "booked") return null;
  if (entry.journalEntryId) return <span>{sv ? "Bokforingsrad" : "Journal row"}: #{entry.journalEntryId}</span>;
  return <div className="bank-journal-link">
    <strong>{sv ? "Koppling saknas" : "Missing journal link"}</strong>
    <button type="button" className="secondary-button" disabled={busy || !token} onClick={() => request(false)}>
      {sv ? "Granska koppling" : "Review link"}
    </button>
    {candidates?.length === 0 && <span>{sv ? "Ingen tillganglig bokforingsrad med samma datum och belopp." : "No available journal row with the same date and amount."}</span>}
    {candidates?.length > 0 && <>
      <select aria-label={sv ? "Bokforingsrad att koppla" : "Journal row to link"} value={selected} disabled={busy} onChange={event => setSelected(event.target.value)}>
        <option value="">{sv ? "Valj bokforingsrad" : "Select journal row"}</option>
        {candidates.map(row => <option key={row.id} value={row.id}>{row.voucherNumber} / #{row.id} / {row.date} / {row.amount} SEK / {row.description}</option>)}
      </select>
      <button type="button" className="secondary-button" disabled={!selected || busy} onClick={() => request(true)}>
        {sv ? "Bekrafta koppling" : "Confirm link"}
      </button>
    </>}
    {error && <span role="alert">{error}</span>}
  </div>;
}
