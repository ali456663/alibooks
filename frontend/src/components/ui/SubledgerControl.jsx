import React, { useEffect, useState } from "react";
import { readSubledgerResponse } from "../../lib/subledger-response.js";
import "./subledger-control.css";

function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export default function SubledgerControl({ apiUrl, token, language }) {
  const sv = language === "sv";
  const [date, setDate] = useState(today);
  const [refresh, setRefresh] = useState(0);
  const [state, setState] = useState({ report: null, error: "", loading: false });
  const requestKey = `${apiUrl}|${token}|${date}|${refresh}|${sv}`;
  const labels = sv
    ? { MATCHED: "Stammer per faktura", NO_DATA: "Inga poster", REVIEW_REQUIRED: "Krav pa granskning", UNSUPPORTED_METHOD: "Manuell metodkontroll kravs" }
    : { MATCHED: "Matched per invoice", NO_DATA: "No entries", REVIEW_REQUIRED: "Review required", UNSUPPORTED_METHOD: "Manual method review required" };

  useEffect(() => {
    const abort = new AbortController();
    setState({ requestKey, report: null, error: "", loading: Boolean(date && token) });
    if (date && token) {
      const fallback = sv ? "Kunde inte verifiera reskontra mot huvudbok." : "Could not verify subledger against general ledger.";
      fetch(`${apiUrl}/subledger-control?asOf=${encodeURIComponent(date)}`, {
        headers: { Authorization: `Bearer ${token}` }, signal: abort.signal
      }).then(response => readSubledgerResponse(response, date, fallback))
        .then(report => { if (!abort.signal.aborted) setState({ requestKey, report, error: "", loading: false }); })
        .catch(error => { if (!abort.signal.aborted) setState({ requestKey, report: null, error: error.message || fallback, loading: false }); });
    }
    return () => abort.abort();
  }, [apiUrl, token, date, refresh, sv, requestKey]);

  const report = state.requestKey === requestKey && state.report?.asOf === date ? state.report : null;
  const money = amount => amount == null ? "-" : new Intl.NumberFormat(sv ? "sv-SE" : "en-GB").format(amount);
  return (
    <section className="subledger-control" aria-labelledby="subledger-heading">
      <form className="subledger-toolbar" onSubmit={event => { event.preventDefault(); setRefresh(value => value + 1); }}>
        <h3 id="subledger-heading">{sv ? "Reskontra mot huvudbok" : "Subledger reconciliation"}</h3>
        <label>{sv ? "Saldodag" : "As of"}<input aria-label={sv ? "Saldodag" : "As of"} type="date" required value={date}
          onChange={event => { setState({ report: null, error: "", loading: false }); setDate(event.target.value); }} /></label>
        <button className="secondary-button" type="submit" disabled={!date || !token || state.loading}>{sv ? "Kontrollera" : "Check"}</button>
      </form>
      {state.loading && <p role="status">{sv ? "Kontrollerar..." : "Checking..."}</p>}
      {state.requestKey === requestKey && state.error && <p role="alert" className="error-message">{state.error}</p>}
      {report && <>
        <p role="status">{labels[report.status]}</p>
        <div className="subledger-table-scroll">
          <table>
            <thead><tr><th>{sv ? "Konto" : "Account"}</th><th>{sv ? "Reskontra SEK" : "Subledger SEK"}</th><th>{sv ? "Huvudbok SEK" : "Ledger SEK"}</th><th>{sv ? "Differens SEK" : "Difference SEK"}</th><th>Status</th></tr></thead>
            <tbody>{report.accounts.map(account => <tr key={account.accountNumber}>
              <th>{account.accountNumber}</th><td>{money(account.subledgerBalance)}</td><td>{money(account.ledgerBalance)}</td><td>{money(account.difference)}</td><td>{labels[account.status]}</td>
            </tr>)}</tbody>
          </table>
        </div>
        {report.accounts.map(account => <div key={`issues-${account.accountNumber}`}>
          {account.unlinkedEntryCount > 0 && <p>{account.accountNumber}: {account.unlinkedEntryCount} {sv ? "bokforingsrader saknar fakturakoppling." : "journal entries have no invoice link."}</p>}
          {account.undatedEntryCount > 0 && <p>{account.accountNumber}: {account.undatedEntryCount} {sv ? "bokforingsrader saknar datum." : "journal entries have no date."}</p>}
          {account.differences.length > 0 && <ul>{account.differences.map(row => <li key={row.invoiceId}>
            {account.accountNumber} / {sv ? "Faktura-ID" : "Invoice ID"} {row.invoiceId}: {sv ? "differens" : "difference"} {money(row.difference)} SEK
          </li>)}</ul>}
        </div>)}
      </>}
    </section>
  );
}
