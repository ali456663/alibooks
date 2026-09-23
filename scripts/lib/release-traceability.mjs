export function isTraceableCheckout({ upstream, githubActions, githubSha, headSha, githubRef }) {
  if (upstream) return true;

  return githubActions === "true"
    && /^[0-9a-f]{40}$/i.test(githubSha || "")
    && /^[0-9a-f]{40}$/i.test(headSha || "")
    && githubSha.toLowerCase() === headSha.toLowerCase()
    && Boolean(githubRef);
}
