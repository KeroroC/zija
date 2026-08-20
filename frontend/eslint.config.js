// @ts-check
/**
 * ESLint 扁平配置（ESLint 9）。
 *
 * 目标（issue #50）：
 * 1. 用 typescript-eslint 为 `tsconfig.app.json` 覆盖的 `src/**` 开启
 *    `@typescript-eslint/no-explicit-any: error`，作为显式 `any` 防复发关口；
 * 2. 测试文件按 vitest 惯用法放行显式 `any`；
 * 3. 接入 `make verify` / CI。
 */
import eslint from '@eslint/js'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'

export default tseslint.config(
  {
    name: 'zija/global-ignores',
    ignores: [
      'dist/**',
      'coverage/**',
      'test-results/**',
      'playwright-report/**',
      'node_modules/**',
      'components.d.ts', // unplugin-vue-components 自动生成
      '**/*.d.ts', // 生成的声明文件
    ],
  },
  // JavaScript 基础规则（会被下方 TS 配置覆盖不适用项）
  eslint.configs.recommended,
  // TypeScript 推荐规则；其中的 no-explicit-any 默认是 warn，下面按文件范围收紧/放行
  ...tseslint.configs.recommended,
  // Vue 基础错误检查（essential 只含防错规则，不含风格规则）
  ...pluginVue.configs['flat/essential'],
  {
    // 应用代码是浏览器环境：声明 DOM/浏览器内置全局
    name: 'zija/browser-globals',
    languageOptions: {
      globals: {
        ...globals.browser,
      },
    },
  },
  {
    name: 'zija/vue-script-ts',
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser, // <script lang="ts"> 块交给 typescript-eslint 解析
        extraFileExtensions: ['.vue'],
        sourceType: 'module',
      },
    },
  },
  {
    // 关口：生产源码（tsconfig.app.json 覆盖的 src/**，排除测试文件）禁止显式 any
    name: 'zija/prod-no-explicit-any',
    files: ['src/**/*.{ts,tsx,vue}'],
    ignores: ['src/**/*.test.ts', 'src/**/*.spec.ts', 'src/**/__tests__/**', 'src/**/test/**'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
    },
  },
  {
    // 测试文件放行显式 any（vitest 惯用法：mock 返回值、vm 调用等）
    name: 'zija/test-allow-any',
    files: ['src/**/*.test.ts', 'src/**/*.spec.ts', 'src/**/__tests__/**', 'src/**/test/**', 'e2e/**/*.ts'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
    },
  },
)
