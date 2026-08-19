package com.zija.ai.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiSettingsMapper extends BaseMapper<AiSettingsEntity> {

    @Insert("""
            INSERT INTO ai_provider_setting(
                singleton_key, enabled, provider_id, outbound_enabled,
                requests_per_minute, member_requests_per_minute,
                max_context_tokens, max_concurrent_requests,
                request_timeout_seconds, version
            ) VALUES (1, FALSE, 'ollama', FALSE, 20, 10, 8192, 2, 30, 0)
            ON CONFLICT (singleton_key) DO NOTHING
            """)
    int insertDefaultIfMissing();
}
