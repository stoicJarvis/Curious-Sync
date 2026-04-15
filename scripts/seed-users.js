const fs = require('fs');
const { execSync } = require("child_process");
const path = require('path');

const DB = "Curious-Sync";
const USER = "postgres";
const PASSWORD = "Admin@1234";
const CSV_PATH = path.join(__dirname, "users_data.csv");

const TOTAL = 5_000_000;

const FIRST_NAMES = [
  "James","Mary","Robert","Patricia","John","Jennifer","Michael","Linda",
  "David","Elizabeth","William","Barbara","Richard","Susan","Joseph","Jessica",
  "Thomas","Sarah","Christopher","Karen","Charles","Lisa","Daniel","Nancy",
  "Matthew","Betty","Anthony","Margaret","Mark","Sandra","Donald","Ashley",
  "Steven","Kimberly","Paul","Emily","Andrew","Donna","Joshua","Michelle",
  "Kenneth","Carol","Kevin","Amanda","Brian","Dorothy","George","Melissa",
  "Timothy","Deborah","Ronald","Stephanie","Edward","Rebecca","Jason","Sharon",
  "Jeffrey","Laura","Ryan","Cynthia","Jacob","Kathleen","Gary","Amy",
  "Nicholas","Angela","Eric","Shirley","Jonathan","Anna","Stephen","Brenda",
  "Larry","Pamela","Justin","Emma","Scott","Nicole","Brandon","Helen",
  "Benjamin","Samantha","Samuel","Katherine","Raymond","Christine","Gregory","Debra",
  "Frank","Rachel","Alexander","Carolyn","Patrick","Janet","Jack","Catherine",
  "Aiden","Olivia","Ethan","Sophia","Noah","Ava","Liam","Mia",
  "Mason","Isabella","Lucas","Charlotte","Logan","Amelia","Oliver","Harper",
];

const LAST_NAMES = [
  "Smith","Johnson","Williams","Brown","Jones","Garcia","Miller","Davis",
  "Rodriguez","Martinez","Hernandez","Lopez","Gonzalez","Wilson","Anderson",
  "Thomas","Taylor","Moore","Jackson","Martin","Lee","Perez","Thompson",
  "White","Harris","Sanchez","Clark","Ramirez","Lewis","Robinson","Walker",
  "Young","Allen","King","Wright","Scott","Torres","Nguyen","Hill",
  "Flores","Green","Adams","Nelson","Baker","Hall","Rivera","Campbell",
  "Mitchell","Carter","Roberts","Gomez","Phillips","Evans","Turner","Diaz",
  "Parker","Cruz","Edwards","Collins","Reyes","Stewart","Morris","Morales",
  "Murphy","Cook","Rogers","Gutierrez","Ortiz","Morgan","Cooper","Peterson",
  "Bailey","Reed","Kelly","Howard","Ramos","Kim","Cox","Ward",
  "Richardson","Watson","Brooks","Chavez","Wood","James","Bennett","Gray",
  "Mendoza","Ruiz","Hughes","Price","Alvarez","Castillo","Sanders","Patel",
  "Myers","Long","Ross","Foster",
];

const DOMAINS = [
  "gmail.com","yahoo.com","outlook.com","hotmail.com","protonmail.com",
  "icloud.com","mail.com","zoho.com","fastmail.com","aol.com",
];

function main() {
  console.log(`Generating CSV for ${TOTAL.toLocaleString()} users...`);
  const start = Date.now();
  
  const writeStream = fs.createWriteStream(CSV_PATH);
  
  for (let i = 0; i < TOTAL; i++) {
    const first = pick(FIRST_NAMES);
    const last = pick(LAST_NAMES);
    const name = `${first} ${last}`;
    const email = `${first.toLowerCase()}.${last.toLowerCase()}${i}@${pick(DOMAINS)}`;
    const password = `${first.charAt(0)}${last}${Math.floor(Math.random() * 9999)}!`;
    
    writeStream.write(`${name},${email},${password}\n`);
    
    if (i % 500000 === 0) console.log(`  Generated ${i.toLocaleString()} rows...`);
  }
  
  writeStream.end();

  writeStream.on('finish', () => {
    console.log("CSV Generated. Starting Postgres COPY...");
    
    // The Magic Command: COPY
    // We use STDIN so we don't have permission issues with the Postgres user reading our local file
    const copyCmd = `psql -h localhost -U ${USER} -d "${DB}" -c "\\copy users(name, email, password) FROM '${CSV_PATH}' WITH DELIMITER ',' CSV"`;
    
    try {
      execSync(copyCmd, { env: { ...process.env, PGPASSWORD: PASSWORD }, stdio: "inherit" });
      const totalTime = ((Date.now() - start) / 1000).toFixed(1);
      console.log(`Done! 5M rows handled in ${totalTime}s`);
      
      // Optional: fs.unlinkSync(CSV_PATH); // Delete CSV after
    } catch (err) {
      console.error("Import failed:", err);
    }
  });
}

function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

main();