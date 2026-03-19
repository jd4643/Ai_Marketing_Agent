import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  headers: { 'Content-Type': 'application/json' },
});

function uuid(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

api.interceptors.request.use((config) => {
  config.headers['X-Request-Id'] = uuid();
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    const msg =
      err.response?.data?.message ?? err.response?.data?.error ?? err.message;
    return Promise.reject(new Error(msg));
  },
);

export default api;
