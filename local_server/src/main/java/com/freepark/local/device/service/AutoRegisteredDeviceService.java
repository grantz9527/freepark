package com.freepark.local.device.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.device.dto.AutoRegisteredDeviceView;
import com.freepark.local.domain.AutoRegisteredDevice;
import com.freepark.local.domain.AutoRegisteredDeviceRepository;
import com.freepark.local.domain.ParkingBarrierRepository;

/**
 * 自动发现设备：设备轮询网关时按 code 自动登记/刷新心跳。
 * 管理人员可在「识别一体机对接」页面查看、收录（转正式登记）或移除。
 */
@Service
public class AutoRegisteredDeviceService {

    private final AutoRegisteredDeviceRepository devices;
    private final ParkingBarrierRepository barriers;

    public AutoRegisteredDeviceService(
            AutoRegisteredDeviceRepository devices, ParkingBarrierRepository barriers) {
        this.devices = devices;
        this.barriers = barriers;
    }

    @Transactional(readOnly = true)
    public List<AutoRegisteredDeviceView> listAll() {
        return devices.findAllByAdoptedFalseOrderByLastPollAtDesc().stream()
                .map(AutoRegisteredDeviceView::from)
                .toList();
    }

    @Transactional
    public void delete(UUID deviceId) {
        if (!devices.existsById(deviceId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        devices.deleteById(deviceId);
    }

    /**
     * 管理人员收录：将该设备标记为已收录（转正式对接列表），
     * 此后该 code 轮询时不再重复自动发现。
     */
    @Transactional
    public void adopt(UUID deviceId) {
        AutoRegisteredDevice device = devices.findById(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        device.markAdopted();
    }

    /**
     * 设备轮询时自动登记：
     * - 不存在：按 code 新建；
     * - 已存在未收录：刷新最后轮询时间；
     * - 已收录（adopted）：仅当正式设备仍存在时忽略；若正式设备已被删除
     *   （收录后未真正创建设备，或设备已移除），则重置为未收录以便重新发现。
     */
    @Transactional
    public void upsertOnPoll(String code) {
        AutoRegisteredDevice device = devices.findByCodeIgnoreCase(code).orElse(null);
        if (device == null) {
            devices.save(new AutoRegisteredDevice(code));
            return;
        }
        if (device.isAdopted()) {
            boolean formalDeviceExists = barriers.findByCodeIgnoreCase(code).isPresent();
            if (formalDeviceExists) {
                return;
            }
            // 已收录但正式设备不存在：重新发现
            device.markNotAdopted();
        }
        device.markPolled(Instant.now());
    }
}
