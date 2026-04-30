const { execSync } = require("child_process");
const fs = require("fs");
const path = require("path");
require("dotenv").config({ path: path.join(__dirname, "..", ".env") });

const DATABASE_URL = process.env.DATABASE_URL || "";
let HOST = process.env.DATABASE_HOST || "localhost";
let PORT = process.env.DATABASE_PORT || 5432;
if (!process.env.DATABASE_HOST && DATABASE_URL) {
  try {
    const url = new URL(DATABASE_URL.replace(/^jdbc:/, ""));
    HOST = url.hostname || HOST;
    PORT = url.port || PORT;
  } catch (err) {
    // fall back to defaults
  }
}
const DB = process.env.DATABASE_NAME || process.env.DB_NAME || "postgres";
const USER =
  process.env.DATABASE_USERNAME || process.env.DB_USERNAME || "postgres";
const PASSWORD =
  process.env.DATABASE_PASSWORD || process.env.DB_PASSWORD || "Admin@1234";

const sql = "SELECT user_id FROM users ORDER BY RANDOM() LIMIT 5000";
const raw = execSync(
  `psql -h ${HOST} -p ${PORT} -U ${USER} -d "${DB}" -t -A -c "${sql}"`,
  { env: { ...process.env, PGPASSWORD: PASSWORD }, encoding: "utf-8" },
).trim();

const userIds = raw.split("\n").filter(Boolean);
console.log(`Extracted ${userIds.length} user IDs`);

fs.writeFileSync(
  path.join(__dirname, "users.json"),
  JSON.stringify(userIds, null, 2),
);
console.log("Saved to k6/users.json");
