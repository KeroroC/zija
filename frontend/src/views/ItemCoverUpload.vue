<template>
  <div class="item-cover-upload">
    <!-- 已有封面 -->
    <div v-if="currentCoverUrl" class="cover-preview">
      <el-image
        :src="currentCoverUrl"
        fit="cover"
        class="cover-image"
        :preview-src-list="[currentCoverUrl]"
      >
        <template #error>
          <div class="image-error">
            <el-icon><i-ep-picture /></el-icon>
            <span>加载失败</span>
          </div>
        </template>
      </el-image>
      <div class="cover-actions">
        <el-button size="small" @click="triggerReplace">
          <el-icon><i-ep-upload /></el-icon>
          替换
        </el-button>
        <el-button size="small" type="danger" @click="handleRemove">
          <el-icon><i-ep-delete /></el-icon>
          移除
        </el-button>
      </div>
    </div>

    <!-- 无封面：上传区域 -->
    <div v-else class="cover-empty">
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :show-file-list="false"
        :limit="1"
        accept="image/jpeg,image/png,image/webp"
        :on-change="onFileSelected"
        drag
        class="cover-uploader"
      >
        <div v-if="uploading" class="upload-progress">
          <el-progress
            :percentage="progress"
            :stroke-width="6"
            :show-text="false"
            status="success"
          />
          <span class="progress-text">上传中 {{ progress }}%</span>
        </div>
        <div v-else class="upload-placeholder">
          <el-icon class="upload-icon"><i-ep-plus /></el-icon>
          <span class="upload-text">拖拽或点击上传封面</span>
          <span class="upload-hint">支持 JPG / PNG / WebP，最大 5 MB</span>
        </div>
      </el-upload>
    </div>

    <!-- 隐藏的文件输入，用于替换场景 -->
    <input
      ref="fileInputRef"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      style="display: none"
      @change="onReplaceFileSelected"
    />

    <!-- 错误提示 -->
    <div v-if="errorMsg" class="error-message">
      <el-icon><i-ep-warning-filled /></el-icon>
      <span>{{ errorMsg }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { uploadItemCover, removeItemCover } from '../api/file'
import type { UploadedFile } from '../api/file'

const props = defineProps<{
  itemId: string
  coverUrl: string | null
  version: number
}>()

const emit = defineEmits<{
  uploaded: [payload: { coverFileId: string; coverUrl: string }]
  removed: []
}>()

const currentCoverUrl = ref<string | null>(props.coverUrl)
const uploading = ref(false)
const progress = ref(0)
const errorMsg = ref('')
const fileInputRef = ref<HTMLInputElement | null>(null)

watch(() => props.coverUrl, (val) => {
  currentCoverUrl.value = val
})

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_SIZE = 5 * 1024 * 1024 // 5 MB

function validateFile(file: File): string | null {
  if (!ALLOWED_TYPES.includes(file.type)) {
    return '仅支持 JPG、PNG、WebP 格式的图片'
  }
  if (file.size > MAX_SIZE) {
    return '文件大小不能超过 5 MB'
  }
  return null
}

function mapServerError(errorCode: string): string {
  switch (errorCode) {
    case 'FILE_TOO_LARGE':
      return '文件大小超过服务端限制'
    case 'FILE_MEDIA_TYPE_UNSUPPORTED':
      return '不支持的文件类型'
    case 'FILE_SIGNATURE_MISMATCH':
      return '文件签名与扩展名不匹配'
    default:
      return '上传失败，请重试'
  }
}

async function doUpload(file: File) {
  const validationError = validateFile(file)
  if (validationError) {
    errorMsg.value = validationError
    return
  }

  errorMsg.value = ''
  uploading.value = true
  progress.value = 0

  // 模拟进度（fetch 不支持原生进度回调）
  const progressTimer = setInterval(() => {
    if (progress.value < 90) {
      progress.value += Math.random() * 15
    }
  }, 200)

  try {
    const result: UploadedFile = await uploadItemCover(props.itemId, file)
    clearInterval(progressTimer)
    progress.value = 100

    currentCoverUrl.value = result.url
    emit('uploaded', { coverFileId: result.id, coverUrl: result.url })
    ElMessage.success('封面上传成功')
  } catch (e: any) {
    clearInterval(progressTimer)
    errorMsg.value = mapServerError(e.errorCode || '')
  } finally {
    uploading.value = false
  }
}

function onFileSelected(uploadFile: any) {
  // el-upload 的 onChange 回调，file 在 uploadFile.raw 中
  const file: File | undefined = uploadFile?.raw
  if (file) {
    doUpload(file)
  }
}

function triggerReplace() {
  fileInputRef.value?.click()
}

function onReplaceFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    doUpload(file)
  }
  // 重置 input 以允许再次选择同一文件
  input.value = ''
}

async function handleRemove() {
  try {
    await ElMessageBox.confirm('确定移除封面图片？', '确认', {
      confirmButtonText: '移除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return // 用户取消
  }

  try {
    await removeItemCover(props.itemId, props.version)
    currentCoverUrl.value = null
    emit('removed')
    ElMessage.success('封面已移除')
  } catch (e: any) {
    errorMsg.value = e.title || '移除失败，请重试'
  }
}
</script>

<style scoped>
.item-cover-upload {
  width: 100%;
}

.cover-preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cover-image {
  width: 100%;
  max-width: 280px;
  aspect-ratio: 1;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
}

.image-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  gap: 4px;
}

.cover-actions {
  display: flex;
  gap: 8px;
}

.cover-empty {
  width: 100%;
  max-width: 280px;
}

.cover-uploader {
  width: 100%;
}

.cover-uploader :deep(.el-upload) {
  width: 100%;
}

.cover-uploader :deep(.el-upload-dragger) {
  width: 100%;
  padding: 24px 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 140px;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.upload-icon {
  font-size: 32px;
  color: var(--el-text-color-placeholder);
}

.upload-text {
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.upload-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.upload-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 80%;
}

.progress-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.error-message {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: var(--el-color-danger);
  font-size: 13px;
}
</style>
