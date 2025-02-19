/**
 * 扩展viteconfig
 * 设置indexPath
 * 打包入口文件路径
 */

import fs from "node:fs";
import path from "node:path";
import type { ResolvedConfig } from "vite";

export interface ViteConfitExtendOptions {
  indexPath: string;
}

export default function viteConfitExtend(options?: ViteConfitExtendOptions) {
  let _config: ResolvedConfig;
  const _options = options;
  return {
    name: "index-path-extend",
    configResolved(config: ResolvedConfig) {
      _config = config;
    },
    closeBundle() {
      if (_config.command == "build" && _options) {
        if (_options.indexPath) {
          try {
            fs.renameSync(
              path.resolve(_config.root, _config.build.outDir, "index.html"),
              path.resolve(_config.root, _config.build.outDir, _options.indexPath)
            );
            console.warn(`indexPath: ${_options.indexPath}`);
          } catch (error) {
            console.log(error)
          }
        }
      }
    },
  };
}