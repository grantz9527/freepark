package com.freepark.local.device.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.device.dto.DeviceCommandView;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.DeviceCommandRepository;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingBarrierRepository;
import com.freepark.local.domain.UserRole;

/**
 * 设备指令排队与出队：admin/规则入队开闸/关闸指令，设备轮询时 FIFO 出队。
 */
@Service
public class DeviceCommandService {

    private final DeviceCommandRepository commands;
    private final ParkingBarrierRepository barriers;
    private final LocalUserRepository users;

    public DeviceCommandService(
            DeviceCommandRepository commands, ParkingBarrierRepository barriers, LocalUserRepository users) {
        this.commands = commands;
        this.barriers = barriers;
        this.users = users;
    }

    @Transactional
    public DeviceCommandView enqueue(UUID requesterId, UUID deviceId, DeviceCommand.Action action, String source) {
        requireAdmin(requesterId);
        ParkingBarrier device = barriers.findById(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!device.isEnabled()) {
            throw new BusinessException(ErrorCode.DEVICE_DISABLED);
        }
        DeviceCommand cmd = commands.save(new DeviceCommand(device, action, source));
        return DeviceCommandView.from(cmd);
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /** 轮询出队：取最早一条 PENDING，标记 DELIVERED 后返回。无则返回 empty。 */
    @Transactional
    public Optional<DeviceCommand> dequeueForDevice(UUID deviceId) {
        Optional<DeviceCommand> pending = commands.findFirstByDeviceIdAndStatusOrderByCreatedAtAsc(
                deviceId, DeviceCommand.Status.PENDING);
        pending.ifPresent(cmd -> cmd.markDelivered(Instant.now()));
        return pending;
    }

    @Transactional(readOnly = true)
    public long countPending(UUID deviceId) {
        return commands.countByDeviceIdAndStatus(deviceId, DeviceCommand.Status.PENDING);
    }

    @Transactional(readOnly = true)
    public List<DeviceCommandView> listPending(UUID deviceId) {
        return commands.findByDeviceIdAndStatusOrderByCreatedAtDesc(deviceId, DeviceCommand.Status.PENDING)
                .stream()
                .map(DeviceCommandView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceCommandView> listRecent(UUID deviceId, int limit) {
        Pageable pageable = PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "createdAt"));
        return commands.findByDeviceIdOrderByCreatedAtDesc(deviceId, pageable)
                .stream()
                .map(DeviceCommandView::from)
                .toList();
    }
}
