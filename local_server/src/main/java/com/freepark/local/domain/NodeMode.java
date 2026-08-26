package com.freepark.local.domain;

public enum NodeMode {

    /** 离线服务：数据仅保存在本机，不上传任何信息。 */
    OFFLINE,

    /** 边缘节点：通过 MQTT 将识别、出入等数据上报到云端平台。 */
    EDGE
}
