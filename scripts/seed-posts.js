const fs = require('fs');
const { execSync } = require("child_process");
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

// ==========================================
// CONFIGURATION
// ==========================================
const CONFIG = {
  db: {
    name: process.env.DATABASE_NAME,
    user: process.env.DATABASE_USERNAME,
    password: process.env.DATABASE_PASSWORD,
    url: process.env.DATABASE_URL,
    host: process.env.DATABASE_HOST || 'localhost',
    port: process.env.DATABASE_PORT || '5432'
  },
  seeder: {
    totalPosts: 1000000,
    csvPath: path.join(__dirname, "posts_data.csv"),
    logInterval: 100000 // Log progress every 100,000 rows
  },
  snowflake: {
    epoch: 1790640000000n, 
    nodeId: 3n,
    nodeBits: 10n,
    sequenceBits: 12n
  }
};

// Fallback logic for DB Host/Port from URL
if (!process.env.DATABASE_HOST && CONFIG.db.url) {
  try {
    const url = new URL(CONFIG.db.url.replace(/^jdbc:/, ''));
    CONFIG.db.host = url.hostname || CONFIG.db.host;
    CONFIG.db.port = url.port || CONFIG.db.port;
  } catch (err) {}
}

// ==========================================
// SNOWFLAKE GENERATOR
// ==========================================
class SnowflakeGenerator {
  constructor(epoch, nodeId, nodeBits, sequenceBits) {
    this.epoch = epoch;
    this.nodeId = nodeId;
    this.sequenceBits = sequenceBits;
    this.nodeShift = sequenceBits;
    this.timestampShift = sequenceBits + nodeBits;
    this.maxSequence = (1n << sequenceBits) - 1n; // 4095
    this.sequence = 0n;
    this.lastTimestamp = -1n;
  }

  nextId() {
    let currentTimestamp = BigInt(Date.now());
    if (currentTimestamp < this.lastTimestamp) throw new Error("Clock moved backwards!");
    
    if (currentTimestamp === this.lastTimestamp) {
      this.sequence = (this.sequence + 1n) & this.maxSequence;
      if (this.sequence === 0n) {
        while (currentTimestamp <= this.lastTimestamp) currentTimestamp = BigInt(Date.now());
      }
    } else {
      this.sequence = 0n;
    }
    
    this.lastTimestamp = currentTimestamp;
    return ((currentTimestamp - this.epoch) << this.timestampShift) | (this.nodeId << this.nodeShift) | this.sequence;
  }
}

// Data Pool
const POST_URLS = [
  "exploring-the-cosmos", "deep-sea-mysteries", "quantum-computing-101", "urban-gardening-tips",
  "history-of-jazz", "machine-learning-basics", "photography-at-golden-hour", "best-hiking-trails",
  "sourdough-bread-recipe", "minimalist-home-design", "electric-vehicles-future", "yoga-for-beginners",
  "indie-game-development", "sustainable-fashion", "psychology-of-habits", "street-food-around-world"
];

// Helper to run raw SQL
function runSQL(sql) {
  return execSync(
    `psql -h ${CONFIG.db.host} -p ${CONFIG.db.port} -U ${CONFIG.db.user} -d "${CONFIG.db.name}" -t -A -c "${sql}"`,
    { 
      env: { ...process.env, PGPASSWORD: CONFIG.db.password }, 
      encoding: "utf-8",
      maxBuffer: 10 * 1024 * 1024 // Increase buffer to 10 MB to hold all 100,000 IDs
    }
  ).trim();
}

// ==========================================
// MAIN EXECUTION
// ==========================================
function main() {
  console.log("Fetching up to 100,000 valid user IDs from the database...");
  
  // Fetch real users. 
  // Using pure lowercase "userid" based on your previous fix.
  let usersListStr;
  try {
    usersListStr = runSQL("SELECT userid FROM users LIMIT 100000");
  } catch (err) {
    console.error("❌ Failed to fetch users from database:");
    if (err.stderr) {
        console.error(err.stderr.toString());
    } else {
        console.error(err.message);
    }
    process.exit(1);
  }

  // Safely clean up hidden \r characters and whitespace that corrupt the CSV
  const userIds = usersListStr.split(/\r?\n/).map(id => id.trim()).filter(Boolean);

  if (userIds.length === 0) {
    console.error("❌ No users found in the database. Please run seed-users.js first!");
    process.exit(1);
  }

  console.log(`Successfully loaded ${userIds.length} users.`);
  console.log(`Generating CSV for ${CONFIG.seeder.totalPosts.toLocaleString()} posts...`);
  
  const start = Date.now();
  const writeStream = fs.createWriteStream(CONFIG.seeder.csvPath);
  const idGenerator = new SnowflakeGenerator(CONFIG.snowflake.epoch, CONFIG.snowflake.nodeId, CONFIG.snowflake.nodeBits, CONFIG.snowflake.sequenceBits);
  
  for (let i = 0; i < CONFIG.seeder.totalPosts; i++) {
    const postId = idGenerator.nextId().toString();
    
    // Pick a random user from the real ones we fetched
    const userId = userIds[Math.floor(Math.random() * userIds.length)];
    
    // Create a unique URL
    const baseUrl = POST_URLS[i % POST_URLS.length];
    const postUrl = `https://curious-sync.app/p/${baseUrl}-${i}`;
    // COPY bypasses Hibernate; createdAt has no DB default, isDeleted is NOT NULL
    const createdAt = new Date().toISOString();

    // Write format: postId, userId, postUrl, createdAt, isDeleted
    writeStream.write(`${postId},${userId},${postUrl},${createdAt},false\n`);
    
    if (i > 0 && i % CONFIG.seeder.logInterval === 0) {
      console.log(`  Generated ${i.toLocaleString()} post rows...`);
    }
  }
  
  writeStream.end();

  writeStream.on('finish', () => {
    console.log("CSV Generated. Starting Postgres COPY...");
    
    // Postgres COPY command
    // Ensure these columns match EXACTLY how they appear in your Postgres database.
    // If your DB has user_id, change 'userid' to 'user_id' below.
    const copyCmd = `psql -h ${CONFIG.db.host} -p ${CONFIG.db.port} -U ${CONFIG.db.user} -d "${CONFIG.db.name}" -c "\\copy posts(postid, userid, posturl, createdat, isdeleted) FROM '${CONFIG.seeder.csvPath}' WITH DELIMITER ',' CSV"`;
    
    try {
      execSync(copyCmd, { env: { ...process.env, PGPASSWORD: CONFIG.db.password }, stdio: "inherit" });
      const totalTime = ((Date.now() - start) / 1000).toFixed(1);
      console.log(`✅ Done! ${CONFIG.seeder.totalPosts.toLocaleString()} posts handled in ${totalTime}s`);
      
      // Cleanup the 50MB+ CSV file
      fs.unlinkSync(CONFIG.seeder.csvPath);
      console.log("Cleaned up temporary CSV file.");
    } catch (err) {
      console.error("\n❌ Import failed!");
      
      // Clearly print the exact Postgres error instead of a giant buffer dump
      if (err.stderr) {
          console.error("--- POSTGRES ERROR DETAILS ---");
          console.error(err.stderr.toString());
      } else {
          console.error(err.message);
      }
    }
  });
}

main();