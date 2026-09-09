package com.lirui.charchat.data.remote

import com.lirui.charchat.data.remote.model.ChatRequest
import kotlinx.coroutines.flow.Flow

/**
 * 对话客户端契约：逐 token 流式返回模型文本。
 * 文本中可能包含协议标记 [[NEXT]] [[PHOTO:...]] [[AFF:...]] [[REL:...]]，
 * 由上层（RoundController / AttributeEngine）解析，本层只透传原文。
 */
interface ChatApi {
    fun stream(req: ChatRequest): Flow<String>
}
