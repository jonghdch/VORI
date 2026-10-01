// 헤더 알림 API.
//   GET  /api/notifications              최근 30개 (최신순)
//   GET  /api/notifications/unread-count { count }
//   POST /api/notifications/{id}/read
//   POST /api/notifications/read-all
//   DELETE /api/notifications             전체 삭제
import { del, get, post } from "./http";

/**
 * @typedef {{ id:number, type:"MONTHLY_REPORT"|"TITLE_ACQUIRED"|"PET_EVOLVED"|"PET_GRADUATE_READY"|"JUDGMENT_OPEN",
 *   title:string, body:string|null, link:string|null, createdAt:string, read:boolean }} AppNotification
 */

/** @returns {Promise<AppNotification[]>} */
export const listNotifications = () => get("/notifications");

/** @returns {Promise<{count:number}>} */
export const getUnreadCount = () => get("/notifications/unread-count");

export const markNotificationRead = (id) => post(`/notifications/${id}/read`);

export const markAllNotificationsRead = () => post("/notifications/read-all");

export const deleteAllNotifications = () => del("/notifications");
