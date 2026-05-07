const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const DB = process.env.DATABASE_NAME;
const USER = process.env.DATABASE_USERNAME;
const PASSWORD = process.env.DATABASE_PASSWORD;
const HOST = process.env.DATABASE_HOST;
const PORT = process.env.DATABASE_PORT;

const NUMBER_OF_EVENTS = 1000000; // 1 Million

function runSQL(sql) {
    try {
        return execSync(
            `psql -h ${HOST} -p ${PORT} -U ${USER} -d "${DB}" -t -A -c "${sql.replace(/"/g, '\\"')}"`,
            { env: { ...process.env, PGPASSWORD: PASSWORD }, encoding: "utf-8" }
        ).trim();
    } catch (err) {
        console.error("SQL failed:", err.message);
        return "";
    }
}

async function main() {
    console.log("Fetching users and posts from database...");

    const userIds = runSQL("SELECT user_id FROM users LIMIT 10000").split("\n").filter(Boolean);
    const postIds = runSQL("SELECT post_id FROM posts LIMIT 1000").split("\n").filter(Boolean);

    if (userIds.length === 0 || postIds.length === 0) {
        console.error("No users or posts found in DB. Please run seed scripts first.");
        process.exit(1);
    }

    console.log(`Found ${userIds.length} users and ${postIds.length} posts.`);
    const filePath = path.join(__dirname, 'kafka-events.jsonl');
    const stream = fs.createWriteStream(filePath);

    console.log(`Generating ${NUMBER_OF_EVENTS.toLocaleString()} events to ${filePath}...`);
    
    const start = Date.now();
    for (let i = 0; i < NUMBER_OF_EVENTS; i++) {
        const userId = userIds[Math.floor(Math.random() * userIds.length)];
        const postId = postIds[Math.floor(Math.random() * postIds.length)];
        const event = {
            userId: userId,
            postId: postId,
            eventType: 'like'
        };
        
        // Use stream.write with drain handling for large file generation
        if (!stream.write(JSON.stringify(event) + '\n')) {
            await new Promise(resolve => stream.once('drain', resolve));
        }

        if (i > 0 && i % 250000 === 0) {
            console.log(`  Progress: ${i.toLocaleString()} events...`);
        }
    }

    stream.end();
    stream.on('finish', () => {
        const duration = ((Date.now() - start) / 1000).toFixed(1);
        console.log(`Done! Generated ${NUMBER_OF_EVENTS} events in ${duration}s`);
    });
}

main();
