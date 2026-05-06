const { execSync } = require("child_process");
const fs = require('fs');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const DB = process.env.DATABASE_NAME;
const USER = process.env.DATABASE_USERNAME;
const PASSWORD = process.env.DATABASE_PASSWORD;
const HOST = process.env.DATABASE_HOST
const PORT = process.env.DATABASE_PORT;

function runSQL(sql) {
    const cmd = `psql -h ${HOST} -p ${PORT} -U ${USER} -d "${DB}" -t -A -c "${sql.replace(/"/g, '\\"')}"`;
    return execSync(cmd, { env: { ...process.env, PGPASSWORD: PASSWORD }, encoding: "utf-8" }).trim();
}

async function main() {
    console.log("--- Exporting Simulation Data ---");

    const usersPath = path.resolve(__dirname, '../k6/users.json');
    const postsPath = path.resolve(__dirname, '../k6/posts.json');

    console.log(`Target Users Path: ${usersPath}`);
    console.log(`Target Posts Path: ${postsPath}`);

    // Export Users
    console.log("Fetching 100k user IDs...");
    const userIds = runSQL("SELECT user_id FROM users LIMIT 100000").split("\n").filter(Boolean);
    console.log(`Fetched ${userIds.length} users. Writing to file...`);
    fs.writeFileSync(usersPath, JSON.stringify(userIds));
    
    // Export Posts
    console.log("Fetching 2000 post IDs...");
    const postIds = runSQL("SELECT post_id FROM posts LIMIT 2000").split("\n").filter(Boolean);
    console.log(`Fetched ${postIds.length} posts. Writing to file...`);
    fs.writeFileSync(postsPath, JSON.stringify(postIds));

    console.log("--- Export Complete ---");
}

main().catch(console.error);
