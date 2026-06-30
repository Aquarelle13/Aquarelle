package com.psp.shifthelper.data.model

data class FatiguePolicy(
    val id: Long = 0,
    val key: String,            // 정책 키 (예: consecutive_limit)
    val enabled: Boolean,       // 활성화 여부
    val description: String     // 설명
)