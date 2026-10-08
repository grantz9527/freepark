package com.freepark.local.whitelist.dto;

/** 一键全量重同步结果：已排队到臻识相机待下发队列（随下次 poll/push 捎带）。 */
public record CameraWhitelistFullResyncView(int cameras, int plates) {
}
