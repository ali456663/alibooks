export function formatMinorAmount(amount, language = "sv") {
  if (!Number.isSafeInteger(amount)) throw new Error("Invalid minor-unit amount.");
  const minor = BigInt(amount);
  const absolute = minor < 0n ? -minor : minor;
  const whole = new Intl.NumberFormat(language === "sv" ? "sv-SE" : "en-US").format(absolute / 100n);
  const fraction = String(absolute % 100n).padStart(2, "0");
  return `${minor < 0n ? "-" : ""}${whole}${language === "sv" ? "," : "."}${fraction} SEK`;
}
