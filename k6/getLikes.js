import { check } from "k6";
import http from "k6/http";

// Load root .env file into a lookup object
const env = {};
open("../.env")
  .trim()
  .split(/\r?\n/)
  .forEach((line) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) return;
    const [key, ...val] = line.split("=");
    if (key && val.length) env[key.trim()] = val.join("=").trim();
  });

export const options = {
  stages: [
    { duration: "15s", target: 50 },
    { duration: "30s", target: 50 },
    { duration: "5s", target: 0 },
  ],
};

export default function () {
  let res = http.get(`${env.GET_LIKES_URL}?postId=${env.POST_ID}`);
  check(res, { "status is 200": (res) => res.status === 200 });
}
