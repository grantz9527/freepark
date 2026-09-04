package com.freepark.local.device.protocol;

import java.nio.charset.Charset;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.RecognitionRecord;
import com.freepark.local.sitesettings.service.SystemSettingsService;
import com.freepark.local.storage.ImageStorageService;

/**
 * 臻识（ZHENSHI）识别相机协议适配。
 *
 * 臻识500等相机采用 HTTP 推送模型：识别到车牌后主动 POST 到服务器，
 * 服务器在 HTTP 响应中返回开闸指令。
 *
 * 推送报文根结构为 AlarmInfoPlate，内含 serialno（设备序列号）、
 * result.PlateResult（车牌、图像、时间戳等）。
 *
 * 响应报文为 Response_AlarmInfoPlate，info="ok" 表示开闸，info="no" 表示不开闸。
 */
@Component
public class ZhenshiProtocol implements CameraProtocol {

    public static final String BRAND = "ZHENSHI";

    /** 落闸默认 IO 控制参数（可经 DeviceCommand.payload 覆盖，见 ioCtrlResponse）。 */
    private static final int DEFAULT_IO = 0;
    private static final int DEFAULT_CLOSE_VALUE = 2;   // 落闸：2 = 先通后断（脉冲），多数道闸触发方式
    private static final int DEFAULT_HOLD_VALUE = 1;    // 常开：1 = 持续通电，闸杆保持抬起
    private static final int DEFAULT_DELAY_MS = 500;

    /**
     * 播报语音经相机 3.6.2 serialData（485 串口数据）下发给外接「显示屏/语音控制板」：
     * 控制板按 0x30 播放语音指令匹配内置语音库发声（喇叭接控制板）。
     */
    private static final int VOICE_SERIAL_CHANNEL = 0;   // 相机串口通道：0 = 485 口 1
    private static final int LED_BOARD_ADDRESS = 0x00;   // 控制板通信地址 DA（默认 0）
    private static final int LED_PROTOCOL_VERSION = 0x64; // 控制板协议版本 VR（100）
    private static final int CMD_PLAY_VOICE = 0x30;      // 0x30 播放语音
    private static final int CMD_LED_TEXT_AND_VOICE = 0x6E; // 0x6E 单包多行显示（LED 文字 + 语音一体）
    private static final int CMD_SYNC_TIME = 0x05;        // 0x05 同步时间（校准控制板 RTC）
    private static final int VOICE_OPT_PLAY = 0x01;      // 0x30 操作字：入队并立即播放
    private static final Charset GBK = Charset.forName("GBK");

    // 0x6E 单包多行约束：最多 4 行、每行文字 GBK 最大 32 字节、语音文本 GBK 最大 64 字节、整包上限 255 字节
    private static final int LED_MAX_LINES = 4;
    private static final int LED_MAX_LINE_BYTES = 32;
    private static final int LED_MAX_VOICE_BYTES = 64;
    // 拦截提示显示参数：立即显示一次并停留 10 秒，避免拦截文字常驻霸屏
    private static final int LED_DISPLAY_MODE = 0x00;   // 0x00 立即显示
    private static final int LED_STAY_SECONDS = 10;     // DT：停留秒数
    private static final int LED_PLAY_COUNT = 1;        // DR：显示 1 次
    private static final byte[] LED_RED_RGBA = {(byte) 0xFF, 0x00, 0x00, 0x00}; // TC：红色文字

    private static final Logger log = LoggerFactory.getLogger(ZhenshiProtocol.class);

    private final ImageStorageService imageStorage;
    private final JsonMapper jsonMapper;
    private final SystemSettingsService systemSettings;

    public ZhenshiProtocol(
            ImageStorageService imageStorage,
            JsonMapper jsonMapper,
            SystemSettingsService systemSettings) {
        this.imageStorage = imageStorage;
        this.jsonMapper = jsonMapper;
        this.systemSettings = systemSettings;
    }

    @Override
    public String brand() {
        return BRAND;
    }

    /**
     * 从推送报文中提取设备序列号，用于匹配 ParkingBarrier.code。
     * 路径：AlarmInfoPlate.serialno
     */
    @Override
    public String extractDeviceId(JsonNode pushData) {
        return pushData.path("AlarmInfoPlate").path("serialno").asText("").trim();
    }

    /**
     * 解析臻识 AlarmInfoPlate 推送报文为识别记录。
     * 关键字段：license（车牌）、imageFile/imageFragmentFile（抓拍图 base64）、
     * direction（行进方向）、timeStamp.Timeval.sec/usec（抓拍时间）。
     */
    @Override
    public RecognitionRecord parsePush(ParkingBarrier device, JsonNode pushData) {
        JsonNode plateResult = pushData.path("AlarmInfoPlate").path("result").path("PlateResult");
        String license = plateResult.path("license").asText("").trim();
        String direction = String.valueOf(plateResult.path("direction").asInt(0));
        PlateColor color = parseColor(plateResult);

        // 抓拍图 base64 落盘到系统设置指定的图片存储目录，数据库只存路径/URL
        String imageRef = null;
        String eventImage = null;
        String rawImage = extractImage(plateResult);
        if (rawImage != null) {
            String relative = imageStorage.saveBase64Image(rawImage, device.getCode());
            imageRef = relative;
            eventImage = imageStorage.toPublicUrl(relative);
        }
        Instant capturedAt = extractTimestamp(plateResult);

        RecognitionRecord record = new RecognitionRecord(device, license, color, imageRef, direction, capturedAt);
        record.setEventImage(eventImage);
        return record;
    }

    /**
     * 生成臻识推送响应：info="ok" 开闸，info="no" 不开闸。
     */
    @Override
    public JsonNode buildPushResponse(boolean openGate) {
        return buildPushResponse(openGate, null);
    }

    /**
     * 生成臻识推送响应，并把播报语音经 3.6.2「控制串口推送 485 数据」组合进同一响应：
     * <pre>
     * {"Response_AlarmInfoPlate":{
     *     "info":"ok",
     *     "serialData":[{"serialChannel":0,"data":"<base64 485帧>","dataLen":28}]
     * }}</pre>
     * serialData 的 data 为「显示屏/语音控制板 0x30 播放语音」完整 485 帧的 base64，
     * 相机收到后发往对应串口，由控制板匹配内置语音库播报（喇叭接控制板）。
     * voiceText 为播报文本（如入口“欢迎光临”、出口“一路顺风”），GBK 编码入帧；
     * 为空/null 时仅返回开闸信息，不附语音。
     */
    @Override
    public JsonNode buildPushResponse(boolean openGate, String voiceText) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode resp = root.putObject("Response_AlarmInfoPlate");
        resp.put("info", openGate ? "ok" : "no");
        resp.put("channelNum", 0);
        resp.put("is_pay", "true");
        if (voiceText != null && !voiceText.isBlank()) {
            appendSerialData(resp, voiceSerialFrame(voiceText));
        }
        return root;
    }

    /**
     * 生成推送响应并附带「LED 文字 + 语音」一体提示（拦截场景如黑名单车辆、离场正常放行两行屏均走此帧）：
     * ledText 非空时组 0x6E 单包多行 485 帧（显示屏显示分行文字并同步按语音库播报），
     * 否则退化为纯语音响应 {@link #buildPushResponse(boolean, String)}。
     */
    @Override
    public JsonNode buildPushResponse(boolean openGate, String voiceText, String ledText) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode resp = root.putObject("Response_AlarmInfoPlate");
        resp.put("info", openGate ? "ok" : "no");
        resp.put("channelNum", 0);
        resp.put("is_pay", "true");
        if (ledText != null && !ledText.isBlank()) {
            appendSerialData(resp, ledVoiceSerialFrame(ledText, voiceText));
        } else if (voiceText != null && !voiceText.isBlank()) {
            appendSerialData(resp, voiceSerialFrame(voiceText));
        }
        return root;
    }

    /**
     * 将一条 485 帧 base64 编码后追加进 serialData 数组（serialChannel=0 发往 485 口 1）。
     * 同一次响应已存在 serialData 时在原数组上继续追加，保证语音帧与时间同步帧可共存（不互相覆盖）。
     */
    private void appendSerialData(ObjectNode resp, byte[] frame) {
        if (frame == null) {
            return;
        }
        JsonNode existing = resp.get("serialData");
        ArrayNode serialData = existing instanceof ArrayNode array ? array : resp.putArray("serialData");
        ObjectNode item = serialData.addObject();
        item.put("serialChannel", VOICE_SERIAL_CHANNEL);
        item.put("data", Base64.getEncoder().encodeToString(frame));
        item.put("dataLen", frame.length);
    }

    /**
     * 组装「播放语音」485 帧（显示屏/语音控制板 0x30 指令，协议文本 GBK 编码，不支持 UNICODE）：
     * <pre>
     * DA + VR + PN[2] + CMD + DL + OTP + TEXT(GBK 最长 254) + CRC16(小端)
     * 例：立即播放“欢迎光临” → 00 64 FF FF 30 09 01 BB B6 D3 AD B9 E2 C1 D9 32 58
     * </pre>
     * 文本含 GBK 无法表达的字符时放弃播报（返回 null），不阻断业务响应。
     */
    private byte[] voiceSerialFrame(String text) {
        byte[] gbk;
        try {
            gbk = text.getBytes(GBK);
        } catch (Exception e) {
            log.warn("语音文本 GBK 编码失败 text={}：{}", text, e.getMessage());
            return null;
        }
        if (gbk.length > 254) {
            log.warn("语音文本超长（GBK {} 字节，上限 254）text={}", gbk.length, text);
            return null;
        }
        int dl = 1 + gbk.length;
        byte[] frame = new byte[7 + gbk.length + 2];
        int i = 0;
        frame[i++] = (byte) LED_BOARD_ADDRESS;
        frame[i++] = (byte) LED_PROTOCOL_VERSION;
        frame[i++] = (byte) 0xFF;   // PN 低字节
        frame[i++] = (byte) 0xFF;   // PN 高字节
        frame[i++] = (byte) CMD_PLAY_VOICE;
        frame[i++] = (byte) dl;
        frame[i++] = (byte) VOICE_OPT_PLAY;
        System.arraycopy(gbk, 0, frame, i, gbk.length);
        i += gbk.length;
        int crc = crc16Modbus(frame, i);
        frame[i++] = (byte) (crc & 0xFF);        // CRC 低字节在前（小端）
        frame[i++] = (byte) ((crc >>> 8) & 0xFF);
        log.info("组装播放语音 485 帧 text={} frame={}", text, toHex(frame));
        return frame;
    }

    /** CRC16-Modbus（初值 0xFFFF、多项式 0xA001，与显示屏/语音控制板协议一致）。 */
    private static int crc16Modbus(byte[] data, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= data[i] & 0xFF;
            for (int b = 0; b < 8; b++) {
                crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0xA001 : crc >>> 1;
            }
        }
        return crc;
    }

    /**
     * 组装「LED 文字 + 语音」一体的 485 帧（显示屏/语音控制板 0x6E 单包多行指令）：
     * <pre>
     * DA + VR + PN[2] + 0x6E + DL
     *   + SAVE_FLAG(临时区) + TEXT_CONTEXT_NUMBER
     *   + 每行 TEXT_CONTEXT：LID + DM + DS + DT + DR + TC[4] + TL + TEXT(GBK) + 行终止(末行 00、其余 0D)
     *   + VF(0A) + VTL + VOICE(GBK) + 00
     *   + CRC16(小端)
     * </pre>
     * ledText 多行以 \n 分隔（最多 4 行、每行 GBK 最长 32 字节）；voiceText 为按控制板语音库词组
     * （词间加逗号）的播报文本（GBK 最长 64 字节）。任一文本编码失败/超长/整包超过 255 字节时
     * 放弃下发（返回 null），不阻断业务响应。
     */
    private byte[] ledVoiceSerialFrame(String ledText, String voiceText) {
        if (voiceText == null || voiceText.isBlank()) {
            log.warn("LED 文字提示缺少语音文本，放弃 0x6E 下发 ledText={}", ledText);
            return null;
        }
        List<byte[]> lines = new ArrayList<>(LED_MAX_LINES);
        for (String raw : ledText.split("\n", -1)) {
            if (lines.size() >= LED_MAX_LINES) {
                break;
            }
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            byte[] gbk;
            try {
                gbk = line.getBytes(GBK);
            } catch (Exception e) {
                log.warn("LED 文字 GBK 编码失败 line={}：{}", line, e.getMessage());
                return null;
            }
            if (gbk.length > LED_MAX_LINE_BYTES) {
                log.warn("LED 单行文字超长（GBK {} 字节，上限 {}）line={}", gbk.length, LED_MAX_LINE_BYTES, line);
                return null;
            }
            lines.add(gbk);
        }
        if (lines.isEmpty()) {
            return null;
        }
        byte[] voice;
        try {
            voice = voiceText.getBytes(GBK);
        } catch (Exception e) {
            log.warn("语音文本 GBK 编码失败 text={}：{}", voiceText, e.getMessage());
            return null;
        }
        if (voice.length > LED_MAX_VOICE_BYTES) {
            log.warn("语音文本超长（GBK {} 字节，上限 {}）text={}", voice.length, LED_MAX_VOICE_BYTES, voiceText);
            return null;
        }
        // DL（SAVE_FLAG 之后）：标志 + 行数 + 各行块 + VF + VTL + 语音 + 语音结束 00
        int bodyLen = 1 + 1 + lines.size() * (5 + 4 + 1) + lines.stream().mapToInt(l -> l.length).sum()
                + lines.size() + 1 + 1 + voice.length + 1;
        if (bodyLen > 255) {
            log.warn("0x6E 单包超长（{} 字节，上限 255）ledText={} voiceText={}", bodyLen, ledText, voiceText);
            return null;
        }
        byte[] frame = new byte[5 + 1 + bodyLen + 2];
        int i = 0;
        frame[i++] = (byte) LED_BOARD_ADDRESS;
        frame[i++] = (byte) LED_PROTOCOL_VERSION;
        frame[i++] = (byte) 0xFF;   // PN 低字节
        frame[i++] = (byte) 0xFF;   // PN 高字节
        frame[i++] = (byte) CMD_LED_TEXT_AND_VOICE;
        frame[i++] = (byte) bodyLen;
        frame[i++] = 0;             // SAVE_FLAG：临时区（频繁修改内容）
        frame[i++] = (byte) lines.size();
        for (int n = 0; n < lines.size(); n++) {
            byte[] line = lines.get(n);
            frame[i++] = (byte) n;                  // LID：行号（0 起）
            frame[i++] = (byte) LED_DISPLAY_MODE;   // DM：立即显示
            frame[i++] = 0;                         // DS：速度
            frame[i++] = (byte) LED_STAY_SECONDS;   // DT：停留秒数
            frame[i++] = (byte) LED_PLAY_COUNT;     // DR：显示次数
            System.arraycopy(LED_RED_RGBA, 0, frame, i, 4); // TC：文字颜色
            i += 4;
            frame[i++] = (byte) line.length;        // TL：文本长度
            System.arraycopy(line, 0, frame, i, line.length);
            i += line.length;
            frame[i++] = (byte) (n == lines.size() - 1 ? 0x00 : 0x0D); // 行终止：末行 00、其余 0D
        }
        frame[i++] = (byte) 0x0A;                   // VF：语音标志（固定 0A）
        frame[i++] = (byte) voice.length;           // VTL：语音文本长度
        System.arraycopy(voice, 0, frame, i, voice.length);
        i += voice.length;
        frame[i++] = 0;                             // 语音 \0 结束
        int crc = crc16Modbus(frame, i);
        frame[i++] = (byte) (crc & 0xFF);           // CRC 低字节在前（小端）
        frame[i++] = (byte) ((crc >>> 8) & 0xFF);
        log.info("组装 LED 文字+语音 485 帧 rows={} voice={} frame={}", lines.size(), voiceText, toHex(frame));
        return frame;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * 轮询返回报文（臻识 comet 轮询模型，老款一体机开启「comet 轮询」后持续来询）。
     *
     * <p>臻识协议中 comet 轮询的回复与识别推送回复结构一致：</p>
     * <ul>
     *   <li>有开闸指令：回复 {"Response_AlarmInfoPlate":{"info":"ok"}}，触发开闸；</li>
     *   <li>有关闸/落杆指令：回复 ivs_ioctrl（IO 控制）报文，由相机 IO 输出驱动道闸落杆/解除常开；</li>
     *   <li>有常开指令：回复 ivs_ioctrl 且 IO 持续通电（value=1），闸杆保持抬起；</li>
     *   <li>无指令（普通心跳确认）：回复 {"status":"ok"}（官方 demo 对设备注册/心跳的标准应答），
     *       相机收到后视为注册成功、继续下一轮询，不做任何业务动作。</li>
     * </ul>
     */
    @Override
    public JsonNode buildPollResponse(DeviceCommand command) {
        return buildPollResponse(command, null);
    }

    @Override
    public JsonNode buildPollResponse(DeviceCommand command, String voiceText) {
        if (command == null) {
            return heartbeatOk();
        }
        if (command.getAction() == DeviceCommand.Action.OPEN) {
            // 指令（手动）开闸：开闸 info=ok，并按设备车道方向附带欢迎/欢送语音（serialData）
            return buildPushResponse(true, voiceText);
        }
        if (command.getAction() == DeviceCommand.Action.CLOSE) {
            // 臻识 HTTP 协议没有独立“关闸字段”；落杆/解除常开经 ivs_ioctrl（IO 控制）下发
            return ioCtrlResponse(command.getPayload(), DEFAULT_CLOSE_VALUE);
        }
        if (command.getAction() == DeviceCommand.Action.HOLD_OPEN) {
            // 常开：IO 持续通电（value=1），闸杆保持抬起
            return ioCtrlResponse(command.getPayload(), DEFAULT_HOLD_VALUE);
        }
        return heartbeatOk();
    }

    /** 识别推送命中预排命令：OPEN → info=ok 开闸（按行进方向附欢迎/欢送语音）；CLOSE → ivs_ioctrl 落闸；HOLD_OPEN → ivs_ioctrl 常开；其余 → 不开闸。 */
    @Override
    public JsonNode buildPushResponse(DeviceCommand command) {
        return buildPushResponse(command, null);
    }

    @Override
    public JsonNode buildPushResponse(DeviceCommand command, String voiceText) {
        if (command != null && command.getAction() == DeviceCommand.Action.CLOSE) {
            return ioCtrlResponse(command.getPayload(), DEFAULT_CLOSE_VALUE);
        }
        if (command != null && command.getAction() == DeviceCommand.Action.HOLD_OPEN) {
            return ioCtrlResponse(command.getPayload(), DEFAULT_HOLD_VALUE);
        }
        return buildPushResponse(command != null && command.getAction() == DeviceCommand.Action.OPEN, voiceText);
    }

    /**
     * 把「主板时间同步」随本次响应一并下发：组 0x05 同步时间 485 帧（取站点时区下的服务器当前墙钟时间），
     * base64 追加进响应 serialData，设备收到后经 485 口 1 转发给出入口控制主板（显示屏/语音控制板）校准 RTC。
     *
     * <p>原响应为心跳 {@code {"status":"ok"}} 等无业务报文时，改写为「不开闸 + serialData」识别应答
     * 以承载时间同步帧（臻识 comet 轮询应答与推送应答结构一致）；帧构造失败时原样返回，不阻断业务。</p>
     */
    @Override
    public JsonNode appendTimeSync(JsonNode response) {
        byte[] frame = timeSyncSerialFrame();
        if (frame == null) {
            return response;
        }
        JsonNode wrap = response.path("Response_AlarmInfoPlate");
        if (wrap.isObject()) {
            appendSerialData((ObjectNode) wrap, frame);
            return response;
        }
        // 心跳/空应答：改回不开闸响应，仅用于携带 serialData 时间同步帧
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode resp = root.putObject("Response_AlarmInfoPlate");
        resp.put("info", "no");
        resp.put("channelNum", 0);
        resp.put("is_pay", "true");
        appendSerialData(resp, frame);
        return root;
    }

    /**
     * 组装「同步主板时间」485 帧（控制板 0x05 指令）：取站点时区下的服务器当前墙钟时间，
     * 站点设置缺失/时间异常时放弃下发（返回 null），不阻断业务响应。
     */
    private byte[] timeSyncSerialFrame() {
        int year;
        int month;
        int day;
        int week;
        int hour;
        int minute;
        int second;
        try {
            ZonedDateTime now = ZonedDateTime.now(systemSettings.getTimezone());
            year = now.getYear();
            month = now.getMonthValue();
            day = now.getDayOfMonth();
            week = boardWeekday(now.getDayOfWeek());
            hour = now.getHour();
            minute = now.getMinute();
            second = now.getSecond();
        } catch (Exception e) {
            log.warn("主板时间同步：读取站点时区/当前时间失败，跳过本次同步：{}", e.getMessage());
            return null;
        }
        if (year < 1970 || year > 2099) {
            log.warn("主板时间同步：年份 {} 超出协议范围 1970~2099，跳过", year);
            return null;
        }
        return syncTimeFrame(year, month, day, week, hour, minute, second);
    }

    /**
     * 组装「同步时间」485 帧（出入口控制主板 0x05 指令）：
     * <pre>
     * DA + VR + PN[2] + 0x05 + DL + Y[2](小端) + M + D + W + H + N + S + CRC16(小端)
     * </pre>
     * DL=8（Y[2]+M+D+W+H+N+S）；Y 为 16 位小端（1970~2099）；W 为协议星期编码
     * （1=星期日、2~7=星期一至六，由 java 周值换算 {@link #boardWeekday(DayOfWeek)}）。
     * 调用方保证 year 在协议范围内。
     */
    private static byte[] syncTimeFrame(int year, int month, int day, int week,
                                        int hour, int minute, int second) {
        byte[] frame = new byte[16];
        int i = 0;
        frame[i++] = (byte) LED_BOARD_ADDRESS;
        frame[i++] = (byte) LED_PROTOCOL_VERSION;
        frame[i++] = (byte) 0xFF;   // PN 低字节
        frame[i++] = (byte) 0xFF;   // PN 高字节
        frame[i++] = (byte) CMD_SYNC_TIME;
        frame[i++] = 8;             // DL：Y[2]+M+D+W+H+N+S 共 8 字节
        frame[i++] = (byte) (year & 0xFF);         // Y 低字节（小端）
        frame[i++] = (byte) ((year >>> 8) & 0xFF); // Y 高字节
        frame[i++] = (byte) month;
        frame[i++] = (byte) day;
        frame[i++] = (byte) week;
        frame[i++] = (byte) hour;
        frame[i++] = (byte) minute;
        frame[i++] = (byte) second;
        int crc = crc16Modbus(frame, i);
        frame[i++] = (byte) (crc & 0xFF);           // CRC 低字节在前（小端）
        frame[i++] = (byte) ((crc >>> 8) & 0xFF);
        log.info("组装主板时间同步 485 帧 y={}-{}-{} W={} {}:{}:{} frame={}",
                year, month, day, week, hour, minute, second, toHex(frame));
        return frame;
    }

    /**
     * 协议星期编码换算：W 取值 1=星期日、2~7=星期一至六。
     * java.time.DayOfWeek（Monday=1 … Sunday=7）→ W = iso % 7 + 1。
     */
    private static int boardWeekday(DayOfWeek dayOfWeek) {
        return dayOfWeek.getValue() % 7 + 1;
    }

    /** 无业务动作的心跳确认应答。 */
    private static JsonNode heartbeatOk() {
        ObjectNode ok = JsonNodeFactory.instance.objectNode();
        ok.put("status", "ok");
        return ok;
    }

    /**
     * 生成臻识 3.6.7「IO 控制」回复，经 comet 轮询/识别推送返回给相机执行：
     * <pre>
     * {"Response_AlarmInfoPlate":{"ivs_ioctrl":{"io":0,"value":2,"delay":500}}}
     * </pre>
     * value：0=断、1=通（常开保持）、2=先通后断（脉冲落闸，delay 生效）。
     * valueFallback 为动作默认值（CLOSE=2 脉冲 / HOLD_OPEN=1 持续通），
     * payload 为可选 JSON 覆盖，如 {"io":0,"value":0}；缺省 io=0、delay=500ms。
     */
    private JsonNode ioCtrlResponse(String payload, int valueFallback) {
        int io = DEFAULT_IO;
        int value = valueFallback;
        int delay = DEFAULT_DELAY_MS;
        if (payload != null && !payload.isBlank()) {
            try {
                JsonNode p = jsonMapper.readTree(payload);
                if (p != null && p.isObject()) {
                    if (p.hasNonNull("io")) {
                        io = p.path("io").asInt(io);
                    }
                    if (p.hasNonNull("value")) {
                        value = p.path("value").asInt(value);
                    }
                    if (p.hasNonNull("delay")) {
                        delay = p.path("delay").asInt(delay);
                    }
                }
            } catch (Exception e) {
                log.warn("ivs_ioctrl 参数解析失败 payload={}：{}", payload, e.getMessage());
            }
        }
        log.info("下发 ivs_ioctrl IO 控制 io={} value={} delay={}ms（payload={}）", io, value, delay,
                payload == null ? "" : payload);
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode resp = root.putObject("Response_AlarmInfoPlate");
        ObjectNode ctrl = resp.putObject("ivs_ioctrl");
        ctrl.put("io", io);
        ctrl.put("value", value);
        if (value == 2) {
            ctrl.put("delay", delay);
        }
        return root;
    }

    private String extractImage(JsonNode plateResult) {
        // 优先大图 imageFile，其次小图 imageFragmentFile
        JsonNode large = plateResult.path("imageFile");
        if (!large.isMissingNode() && !large.isNull() && !large.asText("").isEmpty()) {
            return large.asText();
        }
        JsonNode small = plateResult.path("imageFragmentFile");
        if (!small.isMissingNode() && !small.isNull() && !small.asText("").isEmpty()) {
            return small.asText();
        }
        return null;
    }

    private Instant extractTimestamp(JsonNode plateResult) {
        JsonNode timeval = plateResult.path("timeStamp").path("Timeval");
        if (timeval.has("sec")) {
            long sec = timeval.path("sec").asLong();
            long usec = timeval.path("usec").asLong(0);
            return Instant.ofEpochSecond(sec, usec * 1_000);
        }
        return Instant.now();
    }

    /**
     * 解析臻识 PlateResult 中的车牌颜色字段。
     *
     * 臻识协议车牌颜色字段为 colorType（数字序号）：
     * 0 未知、1 蓝色、2 黄色、3 白色、4 黑色、5 绿色（新能源渐变绿）、6 黄绿（部分固件）。
     * 新固件（MQTT 等）同一取值出现在 plates[].color；个别旧固件用 color。
     * colorName / plateColor 为个别固件或字符串扩展字段，仅作兜底。
     */
    private PlateColor parseColor(JsonNode plateResult) {
        PlateColor fromColorType = mapZhenshiColorType(plateResult.path("colorType").asInt(-1));
        if (fromColorType != null) {
            return fromColorType;
        }
        JsonNode plates = plateResult.path("plates");
        if (plates.isArray() && !plates.isEmpty()) {
            PlateColor fromPlates = mapZhenshiColorType(plates.get(0).path("color").asInt(-1));
            if (fromPlates != null) {
                return fromPlates;
            }
        }
        PlateColor fromColor = mapZhenshiColorType(plateResult.path("color").asInt(-1));
        if (fromColor != null) {
            return fromColor;
        }
        PlateColor fromName = tryColor(plateResult.path("colorName").asText(""));
        if (fromName != null) {
            return fromName;
        }
        return tryColor(plateResult.path("plateColor").asText(""));
    }

    private PlateColor tryColor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return PlateColor.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // 中文/别名映射
            return mapColorAlias(raw.trim());
        }
    }

    /**
     * 臻识 colorType/color 序号 → 系统 PlateColor。
     * 序号与国标车牌颜色一致：1 蓝、2 黄、3 白、4 黑、5 绿（新能源）。
     */
    private PlateColor mapZhenshiColorType(int idx) {
        return switch (idx) {
            case 1 -> PlateColor.BLUE;          // 蓝色（小型汽车）
            case 2 -> PlateColor.YELLOW;        // 黄色（大型汽车等）
            case 3 -> PlateColor.WHITE;         // 白色（警用等）
            case 4 -> PlateColor.BLACK;         // 黑色（涉外）
            case 5 -> PlateColor.GREEN;         // 绿色（新能源渐变绿）
            case 6 -> PlateColor.YELLOW_GREEN;  // 黄绿（大型新能源，部分固件）
            default -> null;                    // 0 未知 及其他
        };
    }

    private PlateColor mapColorAlias(String name) {
        return switch (name) {
            case "蓝", "蓝色", "蓝底", "蓝底白字" -> PlateColor.BLUE;
            case "黄", "黄色", "黄底", "黄底黑字" -> PlateColor.YELLOW;
            case "白", "白色", "白底", "白底黑字" -> PlateColor.WHITE;
            case "黑", "黑色", "黑底", "黑底白字" -> PlateColor.BLACK;
            case "绿", "绿色", "绿底", "绿底黑字", "渐变绿" -> PlateColor.GREEN;
            case "黄绿", "黄绿牌", "黄绿底", "黄绿底黑字", "黄绿双拼" -> PlateColor.YELLOW_GREEN;
            default -> null;
        };
    }
}
