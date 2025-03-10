import axios from "axios";
import { ElMessage } from "element-plus";
const instance = axios.create({});

instance.defaults.withCredentials = true;
instance.defaults.timeout = 500000;
instance.defaults.headers.post["Content-Type"] =
  "application/x-www-form-urlencoded";

function trim(s) {
  return s.replace(/(^\s*)|(\s*$)/g, "");
}

function isString(str) {
  return typeof str == "string" && str.constructor == String;
}

function filter(data) {
  if (data) {
    for (let key in data) {
      data[key] === undefined && delete data[key];
      isString(data[key]) && (data[key] = trim(data[key]));
      if (data[key] && typeof data[key] === "object") {
        for (let innerKey in data[key]) {
          data[key][innerKey] === undefined && delete data[key][innerKey];
          isString(data[key][innerKey]) && trim(data[key][innerKey]);
        }
      }
    }
  }
}

instance.interceptors.request.use((request) => {
  filter(request.params || request.data);
  return request;
});

instance.interceptors.response.use(
  (response) => {
    if (response && response.data) {
      return response.data;
    } else {
      ElMessage.error(response.data);
      return {};
    }
  },
  (error) => {
    ElMessage.error("网络错误");
    return Promise.reject(error);
  }
);

export default instance;
