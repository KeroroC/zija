package com.zija.catalog.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;

import java.util.UUID;

@TableName("catalog_item_tag")
public class ItemTagEntity {
    private UUID householdId;
    private UUID itemId;
    private UUID tagId;

    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }
    public UUID getTagId() { return tagId; }
    public void setTagId(UUID tagId) { this.tagId = tagId; }
}
