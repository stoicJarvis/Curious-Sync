import http from "k6/http";
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";

// 1. Load Data
const users = new SharedArray("users", function () {
  return open("./users.txt").split("\n").filter(line => line.trim() !== "");
});

const posts = new SharedArray("posts", function () {
  return open("./posts.txt").split("\n").filter(line => line.trim() !== "");
});

// 2. Configuration
const BASE_URL = "http://localhost:8000";

export const options = {
  scenarios: {
    million_likes_simulation: {
      executor: "ramping-arrival-rate",
      startRate: 50,
      timeUnit: "1s",
      preAllocatedVUs: 100,
      maxVUs: 500,
      stages: [
        { duration: "1m", target: 500 },  // Ramp up to 500 requests per second
        { duration: "3m", target: 1000 }, // Steady state at 1000 requests per second
        { duration: "1m", target: 0 },    // Ramp down
      ],
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"], // Less than 1% errors
    http_req_duration: ["p(95)<200"], // 95% of requests should be below 200ms
  },
};

// 3. Distribution Logic (Zipf-like)
// This function picks an index biased towards the beginning of the array
function getZipfIndex(arrayLength) {
    // A simple way to simulate a power law distribution:
    // Some posts (at the start of the array) will be much more popular
    return Math.floor(arrayLength * Math.pow(Math.random(), 2.5));
}

export default function () {
  // Randomly pick a user
  const userIdx = Math.floor(Math.random() * users.length);
  const userId = users[userIdx];

  // Pick a post using biased distribution (simulating hot posts)
  const postIdx = getZipfIndex(posts.length);
  const postId = posts[postIdx];

  const payload = JSON.stringify({
    user_id: userId,
    post_id: postId,
  });

  const params = {
    headers: { "Content-Type": "application/json" },
  };

  const res = http.post(`${BASE_URL}/api/likes/react`, payload, params);

  check(res, {
    "is status 200": (r) => r.status === 200,
  });
}
