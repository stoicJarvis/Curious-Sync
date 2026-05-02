/**
 * Curious-Sync — Like Storm Load Test
 * =====================================
 * Simulates millions of like events across multiple posts to verify:
 *   1. Server stays responsive under high concurrency (p95 < 500ms)
 *   2. No requests are lost (error rate < 1%)
 *   3. Like counts are eventually consistent with actual DB rows
 *
 * Run:
 *   brew install k6                          (one-time setup)
 *   k6 run load-tests/like_storm.js          (default: ramp scenario)
 *   k6 run --env SCENARIO=spike load-tests/like_storm.js
 *   k6 run --env SCENARIO=soak  load-tests/like_storm.js
 *
 * After the test, run the SQL consistency check:
 *   SELECT p.post_id, p.total_likes, COUNT(l.like_id) AS actual
 *   FROM posts p LEFT JOIN likes l ON l.post_id = p.post_id
 *   GROUP BY p.post_id, p.total_likes
 *   HAVING p.total_likes != COUNT(l.like_id);
 *   -- Should return 0 rows.
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";
import { SharedArray } from "k6/data";

const usersData = new SharedArray("users", function () {
  return JSON.parse(open("./users.json"));
});

const postsData = new SharedArray("posts", function () {
  return JSON.parse(open("./posts.json"));
});

// ─── Config ──────────────────────────────────────────────────────────────────

const BASE_URL = __ENV.BASE_URL || "http://localhost:8000";

// IDs seeded in setup() — replace with real values if skipping setup
const NUM_USERS = 200;   // virtual users to pre-create
const NUM_POSTS = 10;    // posts to spread likes across

// ─── Custom metrics ───────────────────────────────────────────────────────────

const likesSent     = new Counter("likes_sent");
const likesFailed   = new Rate("likes_failed");
const likeLatency   = new Trend("like_latency_ms", true);
const countLatency  = new Trend("count_latency_ms", true);

// ─── Scenarios ────────────────────────────────────────────────────────────────

const SCENARIO = __ENV.SCENARIO || "ramp";

const scenarios = {
  /**
   * RAMP — gradual load increase, sustain, then cool down.
   * Best for: finding the breaking point and measuring steady-state throughput.
   */
  ramp: {
    executor: "ramping-vus",
    startVUs: 0,
    stages: [
      { duration: "30s", target: 100  },   // warm up
      { duration: "1m",  target: 500  },   // ramp to 500 VUs
      { duration: "2m",  target: 500  },   // sustain
      { duration: "30s", target: 0    },   // cool down
    ],
    gracefulRampDown: "10s",
  },

  /**
   * SPIKE — sudden 2000 VU surge (viral post simulation).
   * Best for: testing how the system absorbs a sudden burst.
   */
  spike: {
    executor: "ramping-vus",
    startVUs: 0,
    stages: [
      { duration: "5s",  target: 2000 },   // instant surge
      { duration: "30s", target: 2000 },   // hold the spike
      { duration: "10s", target: 0    },   // drop back
    ],
    gracefulRampDown: "5s",
  },

  /**
   * SOAK — moderate load for 10 minutes.
   * Best for: catching memory leaks, connection pool exhaustion, and Kafka lag buildup.
   */
  soak: {
    executor: "constant-vus",
    vus: 100,
    duration: "10m",
  },
};

export const options = {
  scenarios: { [SCENARIO]: scenarios[SCENARIO] },
  thresholds: {
    // Core SLOs
    http_req_failed:   ["rate<0.01"],         // < 1% errors
    http_req_duration: ["p(95)<500"],          // p95 under 500ms
    // Custom metrics
    likes_failed:      ["rate<0.01"],
    like_latency_ms:   ["p(95)<400"],
    count_latency_ms:  ["p(95)<200"],
  },
};

// ─── Setup — create test users and posts ─────────────────────────────────────

export function setup() {
  console.log(`\n=== LIKE STORM SETUP ===`);
  console.log(`Loaded ${usersData.length} existing users and ${postsData.length} existing posts.\n`);
  console.log(`\nStarting load test — scenario: ${SCENARIO}\n`);

  // Return the data so it can be used in default function and teardown
  return { users: usersData, posts: postsData };
}

// ─── Default — each VU fires likes in a loop ─────────────────────────────────

export default function (data) {
  const { users, posts } = data;

  if (!users || users.length === 0 || !posts || posts.length === 0) {
    console.warn("No test data — skipping iteration");
    sleep(1);
    return;
  }

  // Each VU picks a random user and a random post
  const userId = users[Math.floor(Math.random() * users.length)];
  const postId = posts[Math.floor(Math.random() * posts.length)];

  // ── Fire like/unlike event ────────────────────────────────────────────────
  const likeStart = Date.now();
  const likeRes = http.post(
    `${BASE_URL}/api/likes/react`,
    JSON.stringify({ user_id: userId, post_id: postId }),
    { headers: { "Content-Type": "application/json" } }
  );
  likeLatency.add(Date.now() - likeStart);
  likesSent.add(1);

  const likeOk = check(likeRes, {
    "like/unlike status 200": (r) => r.status === 200,
    "like/unlike has action field": (r) => {
      try { return JSON.parse(r.body).action !== undefined; } catch { return false; }
    },
  });
  likesFailed.add(!likeOk);

  // ── Occasionally fetch like count (10% of iterations) ────────────────────
  if (Math.random() < 0.1) {
    const countStart = Date.now();
    const countRes = http.get(`${BASE_URL}/api/likes/getLikes?postId=${postId}`);
    countLatency.add(Date.now() - countStart);

    check(countRes, {
      "getLikes status 200": (r) => r.status === 200,
      "getLikes returns number": (r) => !isNaN(Number(r.body)),
    });
  }

  // Brief pause — prevents hammering at full CPU speed
  sleep(Math.random() * 0.5);
}

// ─── Teardown — verify eventual consistency ───────────────────────────────────

export function teardown(data) {
  const { posts } = data;
  if (!posts || posts.length === 0) return;

  console.log("\n=== TEARDOWN — CONSISTENCY CHECK ===");

  // Wait for Kafka consumer to drain
  console.log("Waiting 10s for Kafka consumer to flush remaining events...");
  sleep(10);

  let allConsistent = true;
  for (const postId of posts) {
    const res = http.get(`${BASE_URL}/api/likes/getLikes?postId=${postId}`);
    const count = Number(res.body);
    console.log(`  post=${postId} reported_count=${count}`);
    if (isNaN(count) || res.status !== 200) {
      console.error(`  ✗ Could not fetch count for post ${postId}`);
      allConsistent = false;
    }
  }

  if (allConsistent) {
    console.log("\n✓ All posts returned valid like counts.");
    console.log("  Run the SQL query in the file header to verify DB ↔ count consistency.\n");
  } else {
    console.error("\n✗ Some posts returned invalid counts — check server logs.\n");
  }
}
