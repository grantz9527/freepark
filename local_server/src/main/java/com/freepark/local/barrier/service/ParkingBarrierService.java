package com.freepark.local.barrier.service;

import com.freepark.local.barrier.dto.BarrierView;
import com.freepark.local.barrier.dto.CreateBarrierRequest;
import com.freepark.local.barrier.dto.UpdateBarrierRequest;
import com.freepark.local.barrier.dto.UpdateScreenOrientationRequest;
import com.freepark.local.barrier.dto.UpdateScreenResult;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.device.service.DeviceCommandService;
import com.freepark.local.device.service.DeviceHeartbeatTracker;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingBarrierRepository;
import com.freepark.local.domain.ParkingLane;
import com.freepark.local.domain.ParkingLaneRepository;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.UserRole;

import tools.jackson.databind.json.JsonMapper;

@Service
public class ParkingBarrierService {

    private static final int LED_LINE_MAX_BYTES = 32;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ParkingLaneRepository lanes;
    private final ParkingBarrierRepository barriers;
    private final LocalUserRepository users;
    private final DeviceHeartbeatTracker heartbeats;
    private final DeviceCommandService commands;

    public ParkingBarrierService(
            ParkingLaneRepository lanes,
            ParkingBarrierRepository barriers,
            LocalUserRepository users,
            DeviceHeartbeatTracker heartbeats,
            DeviceCommandService commands) {
        this.lanes = lanes;
        this.barriers = barriers;
        this.users = users;
        this.heartbeats = heartbeats;
        this.commands = commands;
    }

    @Transactional(readOnly = true)
    public List<BarrierView> listBarriers(UUID laneId) {
        requireLane(laneId);
        return barriers.findAllByLaneIdOrderByCreatedAtDesc(laneId).stream()
                .map(this::toLiveView)
                .toList();
    }

    /** 全局设备列表（识别一体机对接页使用），含未绑定车道的设备。 */
    @Transactional(readOnly = true)
    public List<BarrierView> listAll() {
        return barriers.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toLiveView)
                .toList();
    }

    /** 实时设备视图：lastPollAt 取心跳登记中心实时值，online 由心跳新鲜度推导。 */
    private BarrierView toLiveView(ParkingBarrier barrier) {
        Instant last = heartbeats.resolveLastPollAt(barrier.getCode(), barrier.getLastPollAt());
        return BarrierView.from(barrier, last, heartbeats.isOnline(last));
    }

    /** 全局创建设备（可暂不绑定车道，之后通过 bindToLane 绑定）。 */
    @Transactional
    public BarrierView createBarrier(UUID requesterId, CreateBarrierRequest request) {
        requireAdmin(requesterId);
        String code = request.code().trim();
        if (barriers.findByCodeIgnoreCase(code).isPresent()) {
            throw new BusinessException(ErrorCode.BARRIER_CODE_EXISTS);
        }
        boolean enabled = request.enabled() == null || request.enabled();
        ParkingBarrier barrier = new ParkingBarrier(null, request.name(), code, enabled);
        barrier.setBrand(request.brand());
        barrier.setModel(request.model());
        barrier.setConnection(request.host(), request.port());
        barrier.setStreamUrl(request.streamUrl());
        return toLiveView(barriers.save(barrier));
    }

    /** 全局更新设备信息（名称/启用状态/品牌/驱动通道连接参数）。 */
    @Transactional
    public BarrierView updateBarrier(UUID requesterId, UUID barrierId, UpdateBarrierRequest request) {
        requireAdmin(requesterId);
        ParkingBarrier barrier = requireBarrier(barrierId);
        boolean enabled = request.enabled() == null ? barrier.isEnabled() : request.enabled();
        barrier.updateDetails(request.name(), enabled);
        applyConnectionUpdate(barrier, request);
        return toLiveView(barriers.save(barrier));
    }

    /**
     * 保存默认两行、停留秒数和每行播放方式。
     * 至少一行有字时排队 SHOW_DEFAULT，由相机下次轮询把 0x6E 带到控制板。两行都空则只清档案，不下发。
     */
    @Transactional
    public UpdateScreenResult updateScreenOrientation(
            UUID requesterId, UUID barrierId, UpdateScreenOrientationRequest request) {
        requireAdmin(requesterId);
        ParkingBarrier barrier = requireBarrier(barrierId);
        String line1 = normalizeLine(request.line1());
        String line2 = normalizeLine(request.line2());
        int stay = request.staySeconds() == null ? 10 : request.staySeconds();
        int playMode1 = playMode(request.playMode1());
        int playMode2 = playMode(request.playMode2());
        if (stay < 0 || stay > 255 || gbkBytes(line1) > LED_LINE_MAX_BYTES || gbkBytes(line2) > LED_LINE_MAX_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        barrier.setScreenLine1(line1.isEmpty() ? null : line1);
        barrier.setScreenLine2(line2.isEmpty() ? null : line2);
        barrier.setScreenStaySeconds(stay);
        barrier.setScreenPlayMode1(playMode1);
        barrier.setScreenPlayMode2(playMode2);
        ParkingBarrier saved = barriers.save(barrier);
        UUID commandId = null;
        if (saved.isEnabled() && (!line1.isEmpty() || !line2.isEmpty())) {
            String payload = JSON.writeValueAsString(JSON.createObjectNode()
                    .put("line1", line1)
                    .put("line2", line2)
                    .put("staySeconds", stay)
                    .put("playMode1", playMode1)
                    .put("playMode2", playMode2));
            commandId = commands.enqueueSystem(
                    saved.getId(), DeviceCommand.Action.SHOW_DEFAULT, "SCREEN", payload).id();
        }
        return new UpdateScreenResult(toLiveView(saved), commandId);
    }

    /** 0x6E 每行 DM。未传按立即显示。协议之外的取值拒绝保存。 */
    private static final Set<Integer> LED_PLAY_MODES = Set.of(
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x0D, 0x15);

    private static int playMode(Integer raw) {
        int mode = raw == null ? 0x00 : raw;
        if (!LED_PLAY_MODES.contains(mode)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return mode;
    }

    private static String normalizeLine(String raw) {
        return raw == null ? "" : raw.trim();
    }

    /** 与页面一致：ASCII 计 1 字节，其余字符计 2 字节。 */
    private static int gbkBytes(String text) {
        int bytes = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            bytes += cp <= 0x7F ? 1 : 2;
            i += Character.charCount(cp);
        }
        return bytes;
    }

    /** 全局删除设备。 */
    @Transactional
    public void deleteBarrier(UUID requesterId, UUID barrierId) {
        requireAdmin(requesterId);
        ParkingBarrier barrier = requireBarrier(barrierId);
        heartbeats.forget(barrier.getCode());
        barriers.delete(barrier);
    }

    /** 将设备绑定到车道；设备与车道必须已存在，且目标车道内 code 不重复。 */
    @Transactional
    public BarrierView bindToLane(UUID requesterId, UUID barrierId, UUID laneId) {
        requireAdmin(requesterId);
        ParkingBarrier barrier = requireBarrier(barrierId);
        ParkingLane lane = requireLane(laneId);
        if (barrier.getLane() != null && barrier.getLane().getId().equals(laneId)) {
            return toLiveView(barrier);
        }
        if (barriers.existsByLaneIdAndCodeIgnoreCase(laneId, barrier.getCode())) {
            throw new BusinessException(ErrorCode.BARRIER_CODE_EXISTS);
        }
        barrier.setLane(lane);
        return toLiveView(barriers.save(barrier));
    }

    /** 解绑设备与车道的绑定。 */
    @Transactional
    public BarrierView unbind(UUID requesterId, UUID barrierId) {
        requireAdmin(requesterId);
        ParkingBarrier barrier = requireBarrier(barrierId);
        if (barrier.getLane() != null) {
            barrier.setLane(null);
            barriers.save(barrier);
        }
        return toLiveView(barrier);
    }

    @Transactional
    public BarrierView createBarrier(UUID requesterId, UUID laneId, CreateBarrierRequest request) {
        requireAdmin(requesterId);
        ParkingLane lane = requireLane(laneId);
        String code = request.code().trim();
        if (barriers.existsByLaneIdAndCodeIgnoreCase(laneId, code)) {
            throw new BusinessException(ErrorCode.BARRIER_CODE_EXISTS);
        }
        boolean enabled = request.enabled() == null || request.enabled();
        ParkingBarrier barrier = new ParkingBarrier(lane, request.name(), code, enabled);
        barrier.setBrand(request.brand());
        barrier.setModel(request.model());
        barrier.setConnection(request.host(), request.port());
        barrier.setStreamUrl(request.streamUrl());
        return toLiveView(barriers.save(barrier));
    }

    @Transactional
    public BarrierView updateBarrier(
            UUID requesterId, UUID laneId, UUID barrierId, UpdateBarrierRequest request) {
        requireAdmin(requesterId);
        requireLane(laneId);
        ParkingBarrier barrier = requireBarrier(laneId, barrierId);
        boolean enabled = request.enabled() == null ? barrier.isEnabled() : request.enabled();
        barrier.updateDetails(request.name(), enabled);
        applyConnectionUpdate(barrier, request);
        return toLiveView(barriers.save(barrier));
    }

    @Transactional
    public void deleteBarrier(UUID requesterId, UUID laneId, UUID barrierId) {
        requireAdmin(requesterId);
        requireLane(laneId);
        ParkingBarrier barrier = requireBarrier(laneId, barrierId);
        heartbeats.forget(barrier.getCode());
        barriers.delete(barrier);
    }

    /** 全量覆盖驱动通道参数：请求未携带的字段一律清空（PUT 语义，配合表单回填原值）。 */
    private void applyConnectionUpdate(ParkingBarrier barrier, UpdateBarrierRequest request) {
        barrier.setBrand(request.brand());
        barrier.setModel(request.model());
        barrier.setConnection(request.host(), request.port());
        barrier.setStreamUrl(request.streamUrl());
    }

    private ParkingBarrier requireBarrier(UUID laneId, UUID barrierId) {
        ParkingBarrier barrier = requireBarrier(barrierId);
        if (barrier.getLane() == null || !barrier.getLane().getId().equals(laneId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return barrier;
    }

    private ParkingBarrier requireBarrier(UUID barrierId) {
        return barriers.findById(barrierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private ParkingLane requireLane(UUID laneId) {
        return lanes.findById(laneId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
