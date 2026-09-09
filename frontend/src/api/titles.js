import { get, put } from "./http";

/** @returns {Promise<Array<{
 *   id:number|null, code:string, name:string, description:string,
 *   acquired:boolean, active:boolean, current:number, threshold:number,
 *   progressPct:number, acquiredAt:string|null
 * }>>} */
export const listTitles = () => get("/titles");

/** titleId=null clears the active title. */
export const setActiveTitle = (titleId) => put("/titles/active", { titleId });
