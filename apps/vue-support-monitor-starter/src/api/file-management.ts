import { http } from "@repo/utils";
import type { ReturnResult } from "@/types/global";

// 文件信息接口
export interface FileInfo {
  name: string;
  path: string;
  size: number;
  isDirectory: boolean;
  modifiedTime: string;
  permissions?: string;
  owner?: string;
  group?: string;
  children?: FileInfo[];
}

// 文件操作响应接口
export interface FileOperationResponse {
  success: boolean;
  message: string;
  operation: string;
  files?: FileInfo[];
  tree?: FileInfo;
  data?: any;
}

// 文件操作请求接口
export interface FileOperationRequest {
  operation: string;
  path: string;
  newName?: string;
  targetPath?: string;
  recursive?: boolean;
  overwrite?: boolean;
  includeHidden?: boolean;
  sortBy?: string;
  sortOrder?: string;
  maxDepth?: number;
}

/**
 * 获取文件列表
 * @param serverId 服务器ID
 * @param path 文件路径
 * @param includeHidden 是否包含隐藏文件
 * @param sortBy 排序字段
 * @param sortOrder 排序顺序
 * @returns 文件列表
 */
export function getFileList(
  serverId: number,
  path: string,
  includeHidden: boolean = false,
  sortBy: string = "name",
  sortOrder: string = "asc"
) {
  return http.request<ReturnResult<FileOperationResponse>>(
    "get",
    "v1/gen/file-management/list",
    {
      params: {
        serverId,
        path,
        includeHidden,
        sortBy,
        sortOrder,
      },
    }
  );
}

/**
 * 获取文件树
 * @param serverId 服务器ID
 * @param path 根路径
 * @param maxDepth 最大深度
 * @param includeHidden 是否包含隐藏文件
 * @param lazyLoad 是否启用懒加载模式
 * @param pageSize 每页文件数量限制
 * @param pageIndex 页码（从0开始）
 * @returns 文件树
 */
export function getFileTree(
  serverId: number,
  path: string,
  maxDepth: number = 3,
  includeHidden: boolean = false,
  lazyLoad?: boolean,
  pageSize?: number,
  pageIndex?: number
) {
  console.log("API: getFileTree called with", {
    serverId,
    path,
    maxDepth,
    includeHidden,
    lazyLoad,
    pageSize,
    pageIndex,
  });

  const params: any = {
    serverId,
    path,
    maxDepth,
    includeHidden,
  };

  // 添加懒加载参数
  if (lazyLoad !== undefined) {
    params.lazyLoad = lazyLoad;
  }
  if (pageSize !== undefined) {
    params.pageSize = pageSize;
  }
  if (pageIndex !== undefined) {
    params.pageIndex = pageIndex;
  }

  return http.request<ReturnResult<FileOperationResponse>>(
    "get",
    "v1/file-management/tree",
    {
      params,
    }
  );
}

/**
 * 上传文件
 * @param serverId 服务器ID
 * @param targetPath 目标路径
 * @param file 文件
 * @param overwrite 是否覆盖
 * @returns 上传结果
 */
export function uploadFile(
  serverId: number,
  targetPath: string,
  file: File,
  overwrite: boolean = false
) {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("targetPath", targetPath);
  formData.append("overwrite", overwrite.toString());

  return http.request<ReturnResult<FileOperationResponse>>(
    "post",
    `v1/gen/file-management/upload/${serverId}`,
    {
      data: formData,
      headers: {
        "Content-Type": "multipart/form-data",
      },
    }
  );
}

/**
 * 下载文件
 * @param serverId 服务器ID
 * @param filePath 文件路径
 * @returns 文件数据
 */
export function downloadFile(serverId: number, filePath: string) {
  return http.request<Blob>("get", "v1/gen/file-management/download", {
    params: {
      serverId,
      filePath,
    },
    responseType: "blob",
  });
}

/**
 * 创建目录
 * @param serverId 服务器ID
 * @param path 目录路径
 * @param recursive 是否递归创建
 * @returns 创建结果
 */
export function createDirectory(
  serverId: number,
  path: string,
  recursive: boolean = false
) {
  return http.request<ReturnResult<FileOperationResponse>>(
    "post",
    "v1/gen/file-management/mkdir",
    {
      params: {
        serverId,
        path,
        recursive,
      },
    }
  );
}

/**
 * 删除文件或目录
 * @param serverId 服务器ID
 * @param path 文件路径
 * @param recursive 是否递归删除
 * @returns 删除结果
 */
export function deleteFile(
  serverId: number,
  path: string,
  recursive: boolean = false
) {
  return http.request<ReturnResult<FileOperationResponse>>(
    "delete",
    "v1/gen/file-management/delete",
    {
      params: {
        serverId,
        path,
        recursive,
      },
    }
  );
}

/**
 * 重命名文件或目录
 * @param serverId 服务器ID
 * @param oldPath 原路径
 * @param newName 新名称
 * @returns 重命名结果
 */
export function renameFile(serverId: number, oldPath: string, newName: string) {
  return http.request<ReturnResult<FileOperationResponse>>(
    "post",
    "v1/gen/file-management/rename",
    {
      params: {
        serverId,
        oldPath,
        newName,
      },
    }
  );
}

/**
 * 复制文件或目录
 * @param serverId 服务器ID
 * @param sourcePath 源路径
 * @param targetPath 目标路径
 * @param overwrite 是否覆盖
 * @returns 复制结果
 */
export function copyFile(
  serverId: number,
  sourcePath: string,
  targetPath: string,
  overwrite: boolean = false
) {
  return http.request<ReturnResult<FileOperationResponse>>(
    "post",
    "v1/gen/file-management/copy",
    {
      params: {
        serverId,
        sourcePath,
        targetPath,
        overwrite,
      },
    }
  );
}

/**
 * 移动文件或目录
 * @param serverId 服务器ID
 * @param sourcePath 源路径
 * @param targetPath 目标路径
 * @param overwrite 是否覆盖
 * @returns 移动结果
 */
export function moveFile(
  serverId: number,
  sourcePath: string,
  targetPath: string,
  overwrite: boolean = false
) {
  return http.request<ReturnResult<FileOperationResponse>>(
    "post",
    "v1/gen/file-management/move",
    {
      params: {
        serverId,
        sourcePath,
        targetPath,
        overwrite,
      },
    }
  );
}

/**
 * 批量删除文件
 * @param serverId 服务器ID
 * @param paths 文件路径列表
 * @param recursive 是否递归删除
 * @returns 删除结果
 */
export function batchDeleteFiles(
  serverId: number,
  paths: string[],
  recursive: boolean = false
) {
  return http.request<ReturnResult<FileOperationResponse>>(
    "post",
    "v1/gen/file-management/batch-delete",
    {
      data: {
        serverId,
        paths,
        recursive,
      },
    }
  );
}

/**
 * 读取文件内容
 * @param serverId 服务器ID
 * @param filePath 文件路径
 * @returns 文件内容
 */
export function readFileContent(serverId: number, filePath: string) {
  return http.request<ReturnResult<string>>(
    "get",
    "v1/gen/file-management/read",
    {
      params: {
        serverId,
        filePath,
      },
    }
  );
}

/**
 * 保存文件内容
 * @param serverId 服务器ID
 * @param filePath 文件路径
 * @param content 文件内容
 * @returns 保存结果
 */
export function saveFileContent(
  serverId: number,
  filePath: string,
  content: string
) {
  return http.request<ReturnResult<FileOperationResponse>>(
    "post",
    "v1/gen/file-management/write",
    {
      data: {
        serverId,
        filePath,
        content,
      },
    }
  );
}

/**
 * 预览文件
 * @param serverId 服务器ID
 * @param filePath 文件路径
 * @returns 预览结果
 */
export function previewFile(serverId: number, filePath: string) {
  return http.request<ReturnResult<FileOperationResponse>>(
    "get",
    "v1/gen/file-management/preview",
    {
      params: {
        serverId,
        filePath,
      },
    }
  );
}
