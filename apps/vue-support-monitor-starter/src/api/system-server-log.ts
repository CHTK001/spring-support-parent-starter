import { http, type ReturnResult } from "@repo/utils";

export interface SystemServerLogItem {
  id?: number;
  serverId?: number;
  filterType?: string;
  processStatus?: string;
  clientIp?: string;
  clientGeo?: string;
  accessTime?: string;
  durationMs?: number;
  storeTime?: string;
}

export interface LogPageParams {
  current?: number;
  size?: number;
  serverId?: number;
  filterType?: string;
  processStatus?: string;
  clientIp?: string;
  startTime?: string;
  endTime?: string;
}

export function pageSystemServerLogs(params: LogPageParams) {
  return http.request<ReturnResult<{ records: SystemServerLogItem[]; total: number }>>(
    "get",
    "service/systemserver-log/page",
    { params }
  );
}

export function exportSystemServerLogs(params: Omit<LogPageParams, "current" | "size">) {
  return http.request<Blob>("get", "service/systemserver-log/export", {
    params,
    responseType: "blob"
  });
}

export function cleanupSystemServerLogs(beforeTime: string) {
  return http.request<ReturnResult<number>>("delete", "service/systemserver-log/cleanup", {
    params: { beforeTime }
  });
}

