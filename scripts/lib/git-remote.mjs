export function isAliBooksRemote(remote) {
  const value = remote.trim();
  const ssh = value.match(/^git@github\.com:([^\s]+)$/i);
  if (ssh) return ssh[1].replace(/\.git\/?$/i, "").replace(/\/$/, "").toLowerCase() === "ali456663/alibooks";
  try {
    const url = new URL(value);
    return ["https:", "ssh:"].includes(url.protocol) &&
      url.hostname.toLowerCase() === "github.com" &&
      url.pathname.replace(/\.git\/?$/i, "").replace(/\/$/, "").toLowerCase() === "/ali456663/alibooks";
  } catch {
    return false;
  }
}
