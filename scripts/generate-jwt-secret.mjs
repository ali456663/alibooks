import { randomBytes } from "node:crypto";

const secret = randomBytes(48).toString("base64url");

console.log("AliBooks local JWT secret");
console.log("=========================");
console.log("");
console.log("Copy these values into IntelliJ Run Configuration > Environment variables:");
console.log("");
console.log(`JWT_SECRET=${secret}`);
console.log("JWT_EXPIRATION_MINUTES=60");
console.log("");
console.log("Do not paste this value into frontend code, GitHub, screenshots or documentation.");
