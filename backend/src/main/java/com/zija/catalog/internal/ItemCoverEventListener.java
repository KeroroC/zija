package com.zija.catalog.internal;

import com.zija.catalog.internal.persistence.ItemMapper;
import com.zija.file.AttachmentMovedEvent;
import com.zija.file.AttachmentRecycledEvent;
import com.zija.file.FileApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 附件生命周期事件监听：附件离开物品（删除进回收站 / 改挂到别处）时，
 * 同步清除该物品的封面指定（若该附件正是当前封面）。
 *
 * <p>使用普通 {@link EventListener}：与发布方在同一事务内同步执行，
 * 封面状态与附件状态原子一致；测试中无需轮询等待。</p>
 */
@Component
class ItemCoverEventListener {

    private static final Logger log = LoggerFactory.getLogger(ItemCoverEventListener.class);

    private final ItemMapper itemMapper;

    ItemCoverEventListener(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    @EventListener
    public void onAttachmentRecycled(AttachmentRecycledEvent event) {
        if (!FileApi.MOUNT_ITEM.equals(event.mountType())) {
            return;
        }
        clearCover(event.householdId(), event.mountId(), event.fileId());
    }

    @EventListener
    public void onAttachmentMoved(AttachmentMovedEvent event) {
        if (!FileApi.MOUNT_ITEM.equals(event.oldMountType())) {
            return;
        }
        clearCover(event.householdId(), event.oldMountId(), event.fileId());
    }

    private void clearCover(UUID householdId, UUID itemId, UUID fileId) {
        int rows = itemMapper.clearCoverIfCurrent(householdId, itemId, fileId);
        if (rows > 0) {
            log.info("附件离开物品，已清除封面指定: itemId={} fileId={}", itemId, fileId);
        }
    }
}
