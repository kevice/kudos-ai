package io.kudos.ai.ability.model.audio.support.enums.impl

import io.kudos.ai.ability.model.audio.support.enums.ienums.IVoiceEnum
import io.kudos.ai.ability.model.audio.support.enums.ienums.VoiceGender
import io.kudos.ai.ability.model.audio.support.enums.ienums.VoiceType

/**
 * Kokoro TTS 模型的 Voice 枚举
 * 
 * @author K
 * @author AI: Cursor
 * @since 1.0.0
 */
enum class KokoroVoiceEnum(
    override val voiceId: String,
    override val displayName: String? = null,
    override val language: String? = null,
    override val voiceType: VoiceType? = null,
    override val gender: VoiceGender? = null,
    override val isDefault: Boolean = false
) : IVoiceEnum {
    
    // ========== 英文女性 Voices (af_*) ==========
    AF_HEART("af_heart", "Heart", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    AF_ALLOY("af_alloy", "Alloy", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    AF_AOEDE("af_aoede", "Aoede", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    AF_BELLA("af_bella", "Bella", "en", VoiceType.ENGLISH, VoiceGender.FEMALE, isDefault = true),
    AF_JESSICA("af_jessica", "Jessica", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    AF_KORE("af_kore", "Kore", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    AF_NICOLE("af_nicole", "Nicole", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    AF_NOVA("af_nova", "Nova", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    AF_RIVER("af_river", "River", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    AF_SARAH("af_sarah", "Sarah", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    AF_SKY("af_sky", "Sky", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    
    // ========== 英文男性 Voices (am_*) ==========
    AM_ADAM("am_adam", "Adam", "en", VoiceType.ENGLISH, VoiceGender.MALE),
    AM_ECHO("am_echo", "Echo", "en", VoiceType.ENGLISH, VoiceGender.MALE),
    AM_ERIC("am_eric", "Eric", "en", VoiceType.ENGLISH, VoiceGender.MALE),
    AM_FENRIR("am_fenrir", "Fenrir", "en", VoiceType.ENGLISH, VoiceGender.MALE),
    AM_LIAM("am_liam", "Liam", "en", VoiceType.ENGLISH, VoiceGender.MALE),
    AM_MICHAEL("am_michael", "Michael", "en", VoiceType.ENGLISH, VoiceGender.MALE),
    AM_ONYX("am_onyx", "Onyx", "en", VoiceType.ENGLISH, VoiceGender.MALE),
    AM_PUCK("am_puck", "Puck", "en", VoiceType.ENGLISH, VoiceGender.MALE),
    AM_SANTA("am_santa", "Santa", "en", VoiceType.ENGLISH, VoiceGender.MALE),
    
    // ========== 中文女性 Voices (zf_*) ==========
    ZF_XIAOBEI("zf_xiaobei", "小贝", "zh-CN", VoiceType.CHINESE, VoiceGender.FEMALE, isDefault = true),
    ZF_XIAONI("zf_xiaoni", "小妮", "zh-CN", VoiceType.CHINESE, VoiceGender.FEMALE),
    ZF_XIAOXIAO("zf_xiaoxiao", "晓晓", "zh-CN", VoiceType.CHINESE, VoiceGender.FEMALE),
    ZF_XIAOYI("zf_xiaoyi", "晓伊", "zh-CN", VoiceType.CHINESE, VoiceGender.FEMALE),
    
    // ========== 中文男性 Voices (zm_*) ==========
    ZM_YUNJIAN("zm_yunjian", "云健", "zh-CN", VoiceType.CHINESE, VoiceGender.MALE),
    ZM_YUNXI("zm_yunxi", "云希", "zh-CN", VoiceType.CHINESE, VoiceGender.MALE),
    ZM_YUNXIA("zm_yunxia", "云夏", "zh-CN", VoiceType.CHINESE, VoiceGender.MALE),
    ZM_YUNYANG("zm_yunyang", "云扬", "zh-CN", VoiceType.CHINESE, VoiceGender.MALE),
    
    // ========== 其他 Voices ==========
    BF_ALICE("bf_alice", "Alice", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    BF_EMMA("bf_emma", "Emma", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    BF_ISABELLA("bf_isabella", "Isabella", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    BF_LILY("bf_lily", "Lily", "en", VoiceType.ENGLISH, VoiceGender.FEMALE),
    
    BM_DANIEL("bm_daniel", "Daniel", "en", VoiceType.ENGLISH, VoiceGender.MALE),
    BM_FABLE("bm_fable", "Fable", "en", VoiceType.ENGLISH, VoiceGender.MALE),
    BM_GEORGE("bm_george", "George", "en", VoiceType.ENGLISH, VoiceGender.MALE),
    BM_LEWIS("bm_lewis", "Lewis", "en", VoiceType.ENGLISH, VoiceGender.MALE),
    
    JF_ALPHA("jf_alpha", "Alpha", "ja", VoiceType.OTHER, VoiceGender.FEMALE),
    JF_GONGITSUNE("jf_gongitsune", "Gongitsune", "ja", VoiceType.OTHER, VoiceGender.FEMALE),
    JF_NEZUMI("jf_nezumi", "Nezumi", "ja", VoiceType.OTHER, VoiceGender.FEMALE),
    JF_TEBUKURO("jf_tebukuro", "Tebukuro", "ja", VoiceType.OTHER, VoiceGender.FEMALE),
    
    JM_KUMO("jm_kumo", "Kumo", "ja", VoiceType.OTHER, VoiceGender.MALE);
    
    companion object {
        /**
         * 根据 voiceId 查找对应的枚举
         */
        fun fromVoiceId(voiceId: String): KokoroVoiceEnum? {
            return entries.firstOrNull { it.voiceId == voiceId }
        }
        
        /**
         * 获取所有中文 voices
         */
        fun getChineseVoices(): List<KokoroVoiceEnum> {
            return entries.filter { it.voiceType == VoiceType.CHINESE }
        }
        
        /**
         * 获取所有英文 voices
         */
        fun getEnglishVoices(): List<KokoroVoiceEnum> {
            return entries.filter { it.voiceType == VoiceType.ENGLISH }
        }
        
        /**
         * 获取默认 voice
         */
        fun getDefaultVoice(): KokoroVoiceEnum {
            return entries.firstOrNull { it.isDefault } ?: AF_BELLA
        }
        
        /**
         * 获取默认中文 voice
         */
        fun getDefaultChineseVoice(): KokoroVoiceEnum {
            return getChineseVoices().firstOrNull { it.isDefault } ?: ZF_XIAOBEI
        }
    }
}
