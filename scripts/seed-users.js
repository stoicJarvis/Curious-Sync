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
    totalUsers: 10000000, // 10 Million
    csvPath: path.join(__dirname, "users_data.csv"),
    logInterval: 100000 // Log progress every 100,000 rows
  },
  snowflake: {
    // Note: Removed the 'L' suffix as JavaScript uses 'n' for BigInt
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
  } catch (err) {
    // fallback to defaults
  }
}

// ==========================================
// SNOWFLAKE GENERATOR
// ==========================================
class SnowflakeGenerator {
  constructor(epoch, nodeId, nodeBits, sequenceBits) {
    this.epoch = epoch;
    this.nodeId = nodeId;
    
    // Bit shifts
    this.sequenceBits = sequenceBits;
    this.nodeShift = sequenceBits;
    this.timestampShift = sequenceBits + nodeBits;
    
    // Max values
    this.maxSequence = (1n << sequenceBits) - 1n; // 4095
    
    this.sequence = 0n;
    this.lastTimestamp = -1n;
  }

  nextId() {
    let currentTimestamp = BigInt(Date.now());

    if (currentTimestamp < this.lastTimestamp) {
      throw new Error("Clock moved backwards!");
    }

    if (currentTimestamp === this.lastTimestamp) {
      this.sequence = (this.sequence + 1n) & this.maxSequence;
      if (this.sequence === 0n) {
        // Wait for the next millisecond if sequence overflows 4095
        while (currentTimestamp <= this.lastTimestamp) {
          currentTimestamp = BigInt(Date.now());
        }
      }
    } else {
      this.sequence = 0n;
    }

    this.lastTimestamp = currentTimestamp;

    // Shift and combine using BigInt bitwise operations
    return ((currentTimestamp - this.epoch) << this.timestampShift) 
         | (this.nodeId << this.nodeShift) 
         | this.sequence;
  }
}

// ==========================================
// DATA POOLS
// ==========================================
const FIRST_NAMES = ["James","Mary","Robert","Patricia","John","Jennifer","Michael","Linda","David","Elizabeth","William","Barbara","Richard","Susan","Joseph","Jessica","Thomas","Sarah","Christopher","Karen","Charles","Lisa","Daniel","Nancy","Matthew","Betty","Anthony","Margaret","Mark","Sandra","Donald","Ashley","Steven","Kimberly","Paul","Emily","Andrew","Donna","Joshua","Michelle","Kenneth","Carol","Kevin","Amanda","Brian","Dorothy","George","Melissa","Timothy","Deborah","Ronald","Stephanie","Edward","Rebecca","Jason","Sharon","Jeffrey","Laura","Ryan","Cynthia","Jacob","Kathleen","Gary","Amy","Nicholas","Angela","Eric","Shirley","Jonathan","Anna","Stephen","Brenda","Larry","Pamela","Justin","Emma","Scott","Nicole","Brandon","Helen","Benjamin","Samantha","Samuel","Katherine","Raymond","Christine","Gregory","Debra","Frank","Rachel","Alexander","Carolyn","Patrick","Janet","Jack","Catherine","Aiden","Olivia","Ethan","Sophia","Noah","Ava","Liam","Mia","Mason","Isabella","Lucas","Charlotte","Logan","Amelia","Oliver","Harper"];

const LAST_NAMES = ["Smith","Johnson","Williams","Brown","Jones","Garcia","Miller","Davis","Rodriguez","Martinez","Hernandez","Lopez","Gonzalez","Wilson","Anderson","Thomas","Taylor","Moore","Jackson","Martin","Lee","Perez","Thompson","White","Harris","Sanchez","Clark","Ramirez","Lewis","Robinson","Walker","Young","Allen","King","Wright","Scott","Torres","Nguyen","Hill","Flores","Green","Adams","Nelson","Baker","Hall","Rivera","Campbell","Mitchell","Carter","Roberts","Gomez","Phillips","Evans","Turner","Diaz","Parker","Cruz","Edwards","Collins","Reyes","Stewart","Morris","Morales","Murphy","Cook","Rogers","Gutierrez","Ortiz","Morgan","Cooper","Peterson","Bailey","Reed","Kelly","Howard","Ramos","Kim","Cox","Ward","Richardson","Watson","Brooks","Chavez","Wood","James","Bennett","Gray","Mendoza","Ruiz","Hughes","Price","Alvarez","Castillo","Sanders","Patel","Myers","Long","Ross","Foster"];
const DOMAINS = ["gmail.com","yahoo.com","outlook.com","hotmail.com","protonmail.com","icloud.com","mail.com","zoho.com","fastmail.com","aol.com"];

function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

// ==========================================
// MAIN EXECUTION
// ==========================================
function main() {
  console.log(`Generating CSV for ${CONFIG.seeder.totalUsers.toLocaleString()} users...`);
  const start = Date.now();
  
  const writeStream = fs.createWriteStream(CONFIG.seeder.csvPath);
  
  // Initialize Snowflake generator
  const idGenerator = new SnowflakeGenerator(
    CONFIG.snowflake.epoch, 
    CONFIG.snowflake.nodeId, 
    CONFIG.snowflake.nodeBits, 
    CONFIG.snowflake.sequenceBits
  );
  
  for (let i = 0; i < CONFIG.seeder.totalUsers; i++) {
    // Generate Snowflake ID (convert BigInt to String for CSV)
    const userId = idGenerator.nextId().toString();
    
    const first = pick(FIRST_NAMES);
    const last = pick(LAST_NAMES);
    
    // Ensure uniqueness using the iterator `i`
    const name = `${first} ${last}`;
    const username = `${first.toLowerCase()}_${last.toLowerCase()}_${i}`;
    const email = `${first.toLowerCase()}.${last.toLowerCase()}${i}@${pick(DOMAINS)}`;
    const password = `${first.charAt(0)}${last}${Math.floor(Math.random() * 9999)}!`;
    
    // Write new format: userId, name, username, email, password
    writeStream.write(`${userId},${username},${email},${password}\n`);
    
    // Log progress
    if (i > 0 && i % CONFIG.seeder.logInterval === 0) {
      console.log(`  Generated ${i.toLocaleString()} rows...`);
    }
  }
  
  writeStream.end();

  writeStream.on('finish', () => {
    console.log("CSV Generated. Starting Postgres COPY...");
    
    // The Magic Command: COPY
    // NOTE: Because your JPA entity uses @Column(name = "userId"), it is case-sensitive in Postgres.
    // Therefore, "userId" must be wrapped in escaped quotes (\\\"userId\\\").
    const copyCmd = `psql -h ${CONFIG.db.host} -p ${CONFIG.db.port} -U ${CONFIG.db.user} -d "${CONFIG.db.name}" -c "\\copy users(userid, username, email, password) FROM '${CONFIG.seeder.csvPath}' WITH DELIMITER ',' CSV"`;
    
    try {
      execSync(copyCmd, { env: { ...process.env, PGPASSWORD: CONFIG.db.password }, stdio: "inherit" });
      const totalTime = ((Date.now() - start) / 1000).toFixed(1);
      console.log(`Done! ${CONFIG.seeder.totalUsers.toLocaleString()} rows handled in ${totalTime}s`);
      
      // Cleanup
      fs.unlinkSync(CONFIG.seeder.csvPath);
      console.log("Cleaned up temporary CSV file.");
    } catch (err) {
      console.error("Import failed:", err);
    }
  });
}

main();