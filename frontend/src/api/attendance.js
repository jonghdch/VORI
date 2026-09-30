import { get, post } from "./http";

export const getAttendance = () => get("/attendance");
export const getAttendanceMonth = (month) => get(`/attendance/month?month=${encodeURIComponent(month)}`);
export const checkInAttendance = () => post("/attendance");
export const listStatItems = () => get("/attendance/items");
export const consumeStatItem = (id) => post(`/attendance/items/${id}/use`);
