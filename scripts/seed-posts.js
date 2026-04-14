const { execSync } = require("child_process");

const DB = "Curious-Sync";
const USER = process.env.DB_USERNAME || "postgres";
const PASSWORD = process.env.DB_PASSWORD || "Admin@1234";
const HOST = "localhost";
const PORT = 5432;

const POST_URLS = [
  "https://curious-sync.app/p/exploring-the-cosmos",
  "https://curious-sync.app/p/deep-sea-mysteries",
  "https://curious-sync.app/p/quantum-computing-101",
  "https://curious-sync.app/p/urban-gardening-tips",
  "https://curious-sync.app/p/history-of-jazz",
  "https://curious-sync.app/p/machine-learning-basics",
  "https://curious-sync.app/p/photography-at-golden-hour",
  "https://curious-sync.app/p/best-hiking-trails-2026",
  "https://curious-sync.app/p/sourdough-bread-recipe",
  "https://curious-sync.app/p/minimalist-home-design",
  "https://curious-sync.app/p/electric-vehicles-future",
  "https://curious-sync.app/p/yoga-for-beginners",
  "https://curious-sync.app/p/indie-game-development",
  "https://curious-sync.app/p/sustainable-fashion",
  "https://curious-sync.app/p/psychology-of-habits",
  "https://curious-sync.app/p/street-food-around-world",
  "https://curious-sync.app/p/climate-change-solutions",
  "https://curious-sync.app/p/guitar-chord-progressions",
  "https://curious-sync.app/p/remote-work-productivity",
  "https://curious-sync.app/p/ancient-roman-architecture",
  "https://curious-sync.app/p/coffee-brewing-methods",
  "https://curious-sync.app/p/understanding-blockchain",
  "https://curious-sync.app/p/watercolor-painting-intro",
  "https://curious-sync.app/p/marathon-training-plan",
  "https://curious-sync.app/p/diy-home-automation",
  "https://curious-sync.app/p/origami-art-tutorial",
  "https://curious-sync.app/p/astrophotography-guide",
  "https://curious-sync.app/p/vegan-meal-prep-ideas",
  "https://curious-sync.app/p/film-noir-classics",
  "https://curious-sync.app/p/startup-funding-guide",
  "https://curious-sync.app/p/bonsai-care-basics",
  "https://curious-sync.app/p/typescript-best-practices",
  "https://curious-sync.app/p/sleep-science-explained",
  "https://curious-sync.app/p/vintage-vinyl-collecting",
  "https://curious-sync.app/p/rock-climbing-fundamentals",
  "https://curious-sync.app/p/creative-writing-prompts",
  "https://curious-sync.app/p/fermentation-at-home",
  "https://curious-sync.app/p/cybersecurity-essentials",
  "https://curious-sync.app/p/japanese-woodworking",
  "https://curious-sync.app/p/backpacking-southeast-asia",
  "https://curious-sync.app/p/digital-illustration-tips",
  "https://curious-sync.app/p/meditation-for-focus",
  "https://curious-sync.app/p/open-source-contributions",
  "https://curious-sync.app/p/chess-openings-guide",
  "https://curious-sync.app/p/tiny-house-living",
  "https://curious-sync.app/p/podcast-production-101",
  "https://curious-sync.app/p/ocean-conservation",
  "https://curious-sync.app/p/3d-printing-projects",
  "https://curious-sync.app/p/philosophy-of-stoicism",
  "https://curious-sync.app/p/modern-calligraphy",
];

function runSQL(sql) {
  return execSync(
    `psql -h ${HOST} -p ${PORT} -U ${USER} -d "${DB}" -t -A -c "${sql.replace(/"/g, '\\"')}"`,
    { env: { ...process.env, PGPASSWORD: PASSWORD }, encoding: "utf-8" }
  ).trim();
}

function main() {
  // Grab 50 random user_ids from the users table
  const userIds = runSQL("SELECT user_id FROM users ORDER BY RANDOM() LIMIT 50")
    .split("\n")
    .filter(Boolean);

  if (userIds.length === 0) {
    console.error("No users found. Run seed-users.js first.");
    process.exit(1);
  }

  console.log(`Found ${userIds.length} users. Inserting 50 posts...`);

  const rows = [];
  for (let i = 0; i < 50; i++) {
    const userId = userIds[i % userIds.length];
    const likes = Math.floor(Math.random() * 5000);
    const url = POST_URLS[i];
    rows.push(`(gen_random_uuid(),'${userId}',${likes},'${url}')`);
  }

  const sql = `INSERT INTO posts (post_id, user_id, total_likes, post_url) VALUES ${rows.join(",")};`;
  runSQL(sql);

  console.log("Done! Inserted 50 posts.");
}

main();
