const { execSync } = require("child_process");
const fs = require("fs");
const path = require("path");

// Load .env file into process.env
const envPath = path.join(__dirname, ".env");
fs.readFileSync(envPath, "utf-8").split("\n").forEach((line) => {
  const [key, ...val] = line.split("=");
  if (key && val.length) process.env[key.trim()] = val.join("=").trim();
});

const sql = "SELECT user_id FROM users ORDER BY RANDOM() LIMIT 5000";
const raw = execSync(
  `psql -h localhost -U ${process.env.DB_USERNAME} -d "${process.env.DB_NAME}" -t -A -c "${sql}"`,
  { env: { ...process.env, PGPASSWORD: process.env.DB_PASSWORD }, encoding: "utf-8" },
).trim();

const userIds = raw.split("\n").filter(Boolean);
console.log(`Extracted ${userIds.length} user IDs`);

fs.writeFileSync(
  path.join(__dirname, "users.json"),
  JSON.stringify(userIds, null, 2),
);
console.log("Saved to k6/users.json");
