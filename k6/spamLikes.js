import http from "k6/http";
import { check } from "k6";
import { SharedArray } from "k6/data";
import exec from "k6/execution";

// Fallback parser since the k6 dotenv jslib URL is down/404ing
function parseDotEnv(content) {
  const result = {};
  content.split(/\r?\n/).forEach((line) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) return;
    const [key, ...val] = line.split("=");
    if (key && val.length) result[key.trim()] = val.join("=").trim();
  });
  return result;
}

let env = {};
try {
  env = parseDotEnv(open("../.env"));
} catch (e) {
  // ignore if .env does not exist
}

const users = new SharedArray("users", function () {
  return JSON.parse(open("./users.json"));
});

const LIKES = parseInt(__ENV.LIKES || env.LIKES || "50", 10);
const POST_ID = __ENV.POST_ID || env.POST_ID;
const BASE_URL = __ENV.BASE_URL || env.BASE_URL || "http://localhost:8000";

if (LIKES > users.length) {
  throw new Error(`Requested ${LIKES} likes, but users.json only contains ${users.length} users. Run node extract-users.js to extract more users.`);
}

export const options = {
  scenarios: {
    exact_likes: {
      executor: "shared-iterations",
      vus: Math.min(LIKES, 100), // Concurrency limit
      iterations: LIKES,         // Exactly 'x' total iterations
      maxDuration: "5m",
    },
  },
};

export default function () {
  // exec.scenario.iterationInTest gives a unique index (0 to LIKES - 1) across all VUs
  const iterationIdx = exec.scenario.iterationInTest;
  
  // Pick a distinct user for each iteration
  const userId = users[iterationIdx];

  const payload = JSON.stringify({
    user_id: userId,
    post_id: POST_ID,
  });

  const params = {
    headers: { "Content-Type": "application/json" },
  };

  const res = http.post(`${BASE_URL}/api/likes/react`, payload, params);

  check(res, {
    "status is 200": (r) => r.status === 200,
  });
}
