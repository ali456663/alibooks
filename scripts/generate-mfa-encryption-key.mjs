import { randomBytes } from "node:crypto";

console.log("AliBooks MFA encryption key (keep separate from the database backup):");
console.log(`APP_AUTH_MFA_ENCRYPTION_KEY=${randomBytes(32).toString("base64")}`);
console.log("Set APP_AUTH_REQUIRE_MFA=true only for production. Never commit or share this key.");
