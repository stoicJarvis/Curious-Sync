import { check } from "k6";
import { SharedArray } from "k6/data";
import http from "k6/http";

// Load .env file into a lookup object
const env = {};
open("./.env")
  .trim()
  .split("\n")
  .forEach((line) => {
    const [key, ...val] = line.split("=");
    if (key && val.length) env[key.trim()] = val.join("=").trim();
  });

const users = new SharedArray("users", function () {
  return JSON.parse(open("./users.json"));
});

export const options = {
  scenarios: {
    like_post: {
      executor: "per-vu-iterations",
      vus: 5000,
      iterations: 1, // each VU fires exactly once
      maxDuration: "2m",
    },
  },
};

export default function () {
  // Each VU has a unique __VU (1-based), pick a unique user
  const userId = users[__VU - 1];

  const payload = JSON.stringify({
    user_id: userId,
    post_id: env.POST_ID,
  });

  const params = {
    headers: { "Content-Type": "application/json" },
  };

  const res = http.post(`${env.REACT_BASE_URL}`, payload, params);

  check(res, {
    "status is 200": (r) => r.status === 200,
  });
}
